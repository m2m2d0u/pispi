package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.callback.RetourFondsCallbackPayload;
import ci.sycapay.pispi.dto.callback.RetourFondsDemandeCallbackPayload;
import ci.sycapay.pispi.dto.callback.RetourFondsRejetCallbackPayload;
import ci.sycapay.pispi.entity.PiReturnExecution;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiReturnExecutionRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.util.IdGenerator;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hidden from OpenAPI/Swagger for indirect-participant deployments — the
 * return-of-funds flow runs between settlement accounts that indirect
 * participants (EMEs) do not hold at the central bank.
 */
@Hidden
@Tag(name = "Return Funds Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReturnFundsCallbackController {

    private final MessageLogService messageLogService;
    private final PiReturnRequestRepository returnRequestRepository;
    private final PiReturnExecutionRepository returnExecutionRepository;
    private final PiTransferRepository transferRepository;
    private final WebhookService webhookService;
    private final AipClient aipClient;
    private final PiSpiProperties properties;

    @Operation(summary = "Receive inbound return-of-funds request (CAMT.056)",
            description = "Called by the AIP when another participant requests a return of funds for a transfer they sent to this PI.\n\n"
                    + "Implements BCEAO §4.8 « Traitement du message camt.056 » avec 3 scénarios :\n"
                    + "1. Transfert déjà retourné (RTND) → auto-rejet camt.029 ARDT, sans notifier le client.\n"
                    + "2. Compte client clôturé → auto-rejet camt.029 AC04 (à brancher via hook backend).\n"
                    + "3. Sinon → persiste en PENDING + webhook RETURN_REQUEST_RECEIVED pour décision client.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetourFondsDemandeCallbackPayload.class)))
    @PostMapping("/retour-fonds/demande")
    @Transactional
    public ResponseEntity<Void> receiveReturnRequest(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("CAMT.056 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");
        String codeMembrePayeur = (String) payload.get("codeMembreParticipantPayeur");
        String raisonOriginale = (String) payload.get("raison");

        // Dédup stricte par msgId — re-livraison AIP du même camt.056.
        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.CAMT_056, MessageDirection.INBOUND, payload, 202, null);

        // Idempotence sur (endToEndId, INBOUND, PENDING) : BCEAO autorise les
        // retries du camt.056 avec un msgId différent. Si une demande PENDING
        // existe déjà pour cet e2e, on ne re-notifie pas le client mais on
        // accepte le message AIP-side (202).
        Optional<PiReturnRequest> existingPending = returnRequestRepository
                .findFirstByEndToEndIdAndDirectionAndStatut(
                        endToEndId, MessageDirection.INBOUND, ReturnRequestStatus.PENDING);
        if (existingPending.isPresent()) {
            log.info("CAMT.056 INBOUND ignoré (idempotence) : demande déjà PENDING "
                    + "[endToEndId={}, identifiantDemande={}]",
                    endToEndId, existingPending.get().getIdentifiantDemande());
            return ResponseEntity.accepted().build();
        }

        // Lookup du transfer original — nous sommes le payé (direction=INBOUND).
        Optional<PiTransfer> transferOpt = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND);

        // -------------------------------------------------------------
        // Scénario B1 — transfert déjà retourné : auto-rejet camt.029 ARDT
        // -------------------------------------------------------------
        // BCEAO §4.8 : « Si le transfert de fonds a déjà été retournée, PI
        // rejette la demande en envoyant un camt.029 avec le code ARDT. »
        // On détecte via PiTransfer.statut=RTND OU PiReturnExecution déjà
        // enregistrée pour cet e2e.
        boolean alreadyReturned = transferOpt.map(t -> t.getStatut() == TransferStatus.RTND)
                .orElse(false)
                || returnExecutionRepository.findByEndToEndId(endToEndId).isPresent();
        if (alreadyReturned) {
            autoRejectInbound(msgId, endToEndId, codeMembrePayeur, raisonOriginale,
                    CodeRaisonRetourFonds.ARDT,
                    "transfert déjà retourné (PACS.004 antérieur)");
            return ResponseEntity.accepted().build();
        }

        // -------------------------------------------------------------
        // Scénario B2 — compte client clôturé : auto-rejet camt.029 AC04
        // -------------------------------------------------------------
        // BCEAO §4.8 : « Si le client a clôturé son compte dans vos livres,
        // vous devez rejeter directement en utilisant le code AC04. »
        //
        // Le statut compte du client payé n'est pas tracké directement par
        // PI-SPI (responsabilité du back-office métier). On délègue donc la
        // détection au backend via le webhook RETURN_REQUEST_RECEIVED ci-
        // dessous : si le backend constate que le compte est clôturé, il
        // appelle l'endpoint reject avec raison=AC04. Cette branche reste
        // ouverte pour une détection inline future si on stocke le statut
        // compte localement (par ex. via un strategy AccountStatusChecker).

        // -------------------------------------------------------------
        // Scénario B3 — flux nominal : PENDING + webhook
        // -------------------------------------------------------------
        // identifiantDemandeRetourFonds est un champ AIP interne pas garanti
        // dans le payload INBOUND ; on retombe sur msgId pour satisfaire la
        // contrainte NOT NULL locale.
        String identifiantDemande = (String) payload.get("identifiantDemandeRetourFonds");
        if (identifiantDemande == null || identifiantDemande.isBlank()) {
            identifiantDemande = msgId;
        }

        // Parse défensif sur la raison BCEAO. Une raison inconnue ne doit pas
        // bloquer le callback — on logue + on persiste avec raison=null.
        CodeRaisonDemandeRetourFonds raisonEnum = null;
        if (raisonOriginale != null && !raisonOriginale.isBlank()) {
            try {
                raisonEnum = CodeRaisonDemandeRetourFonds.valueOf(raisonOriginale);
            } catch (IllegalArgumentException e) {
                log.warn("CAMT.056 raison inconnue '{}' [endToEndId={}] — persistée sans code",
                        raisonOriginale, endToEndId);
            }
        }

        PiReturnRequest req = PiReturnRequest.builder()
                .msgId(msgId)
                .identifiantDemande(identifiantDemande)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .codeMembrePayeur(codeMembrePayeur)
                .raison(raisonEnum)
                .statut(ReturnRequestStatus.PENDING)
                .build();
        returnRequestRepository.save(req);

        webhookService.notify(WebhookEventType.RETURN_REQUEST_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    /**
     * Auto-rejet d'un camt.056 INBOUND sans intervention du client. Émet le
     * camt.029 OUTBOUND vers l'AIP avec {@code statut=RJCR} + raison
     * (typiquement {@code ARDT} ou {@code AC04}), persiste la ligne
     * {@code PiReturnRequest} directement en {@code RJCR} pour traçabilité,
     * et <strong>ne fire pas</strong> le webhook (BCEAO impose de ne pas
     * notifier le client dans ces deux scénarios).
     */
    private void autoRejectInbound(String origMsgId, String endToEndId,
                                    String codeMembrePayeur, String raisonOriginale,
                                    CodeRaisonRetourFonds raisonRejet, String motif) {
        String codeMembre = properties.getCodeMembre();
        String responseMsgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> camt029 = new HashMap<>();
        camt029.put("msgId", responseMsgId);
        camt029.put("msgIdDemande", origMsgId);
        camt029.put("codeMembreParticipantPayeur", codeMembrePayeur);
        camt029.put("statut", "RJCR");
        camt029.put("endToEndId", endToEndId);
        camt029.put("raison", raisonRejet.name());

        try {
            messageLogService.log(responseMsgId, endToEndId, IsoMessageType.CAMT_029,
                    MessageDirection.OUTBOUND, camt029, null, null);
            aipClient.post("/retour-fonds/reponses", camt029);

            // Persister en RJCR pour traçabilité — sans webhook (BCEAO exige
            // explicitement de ne pas notifier le client sur ces auto-rejets).
            CodeRaisonDemandeRetourFonds raisonDemandeEnum = null;
            if (raisonOriginale != null && !raisonOriginale.isBlank()) {
                try {
                    raisonDemandeEnum = CodeRaisonDemandeRetourFonds.valueOf(raisonOriginale);
                } catch (IllegalArgumentException ignored) {
                    // raison inconnue — on persiste null, pas critique
                }
            }
            PiReturnRequest req = PiReturnRequest.builder()
                    .msgId(origMsgId)
                    .identifiantDemande(origMsgId)
                    .endToEndId(endToEndId)
                    .direction(MessageDirection.INBOUND)
                    .codeMembrePayeur(codeMembrePayeur)
                    .raison(raisonDemandeEnum)
                    .raisonRejet(raisonRejet)
                    .msgIdRejet(responseMsgId)
                    .statut(ReturnRequestStatus.RJCR)
                    .build();
            returnRequestRepository.save(req);

            log.info("CAMT.056 auto-rejeté [endToEndId={}, raisonRejet={}, motif={}]",
                    endToEndId, raisonRejet, motif);
        } catch (Exception ex) {
            log.error("Échec auto-rejet CAMT.029 [endToEndId={}, raisonRejet={}] — "
                    + "callback toujours acquitté en 202 pour éviter la redélivrance AIP",
                    endToEndId, raisonRejet, ex);
        }
    }

    @Operation(summary = "Receive return rejection (CAMT.029)", description = "Called by the AIP when the receiving PI rejects a return-of-funds request initiated by this PI. Updates local return request status to RJCR.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetourFondsRejetCallbackPayload.class)))
    @PostMapping("/retour-fonds/reponses")
    public ResponseEntity<Void> receiveReturnRejection(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("CAMT.029 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");

        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.CAMT_029, MessageDirection.INBOUND, payload, 202, null);

        // Parse défensif sur la raison BCEAO (cf. CodeRaisonRetourFonds :
        // ARDT|AC04|CUST|...) — un code inconnu (typo, valeur future) ne
        // doit pas planter le callback.
        String raisonRaw = (String) payload.get("raison");
        CodeRaisonRetourFonds raisonRejet = null;
        if (raisonRaw != null && !raisonRaw.isBlank()) {
            try {
                raisonRejet = CodeRaisonRetourFonds.valueOf(raisonRaw);
            } catch (IllegalArgumentException e) {
                log.warn("CAMT.029 raison inconnue '{}' [endToEndId={}] — "
                        + "rejet appliqué sans code", raisonRaw, endToEndId);
            }
        }
        final CodeRaisonRetourFonds finalRaisonRejet = raisonRejet;

        returnRequestRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND).ifPresent(req -> {
            // Garde idempotence : un camt.029 retardataire ne doit pas
            // écraser une demande déjà ACCEPTED (PACS.004 arrivé avant).
            if (req.getStatut() == ReturnRequestStatus.ACCEPTED
                    || req.getStatut() == ReturnRequestStatus.RJCR) {
                log.warn("CAMT.029 ignoré sur demande déjà finalisée "
                        + "[endToEndId={}, statut={}]", endToEndId, req.getStatut());
                return;
            }
            req.setStatut(ReturnRequestStatus.RJCR);
            req.setRaisonRejet(finalRaisonRejet);
            req.setMsgIdRejet(msgId);
            returnRequestRepository.save(req);
        });

        webhookService.notify(WebhookEventType.RETURN_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive return execution (PACS.004)", description = "Called by the AIP when the receiving PI accepts a return and transfers funds back. Saves the return execution record locally, marks the originating PiReturnRequest as ACCEPTED, transitions the related PiTransfer to RTND (Returned), and fires a RETURN_EXECUTED webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetourFondsCallbackPayload.class)))
    @PostMapping("/retour-fonds")
    public ResponseEntity<Void> receiveReturnExecution(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("PACS.004 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");
        String raisonRetourRaw = (String) payload.get("raisonRetour");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_004, MessageDirection.INBOUND, payload, 202, null);

        // Parse défensif sur raisonRetour : un code inconnu (typo, valeur
        // future BCEAO) ne doit pas planter le callback. On logue et on
        // poursuit avec raisonRetour=null sur l'execution row.
        CodeRaisonRetourFonds raisonRetour = null;
        if (raisonRetourRaw != null && !raisonRetourRaw.isBlank()) {
            try {
                raisonRetour = CodeRaisonRetourFonds.valueOf(raisonRetourRaw);
            } catch (IllegalArgumentException e) {
                log.warn("PACS.004 raisonRetour inconnu '{}' [endToEndId={}] — "
                        + "execution enregistrée sans code", raisonRetourRaw, endToEndId);
            }
        }

        BigDecimal montantRetourne = payload.get("montantRetourne") != null
                ? new BigDecimal(String.valueOf(payload.get("montantRetourne")))
                : null;

        // Finaliser la PiReturnRequest OUTBOUND associée : le PACS.004 INBOUND
        // signifie que la contrepartie a accepté la demande d'annulation et a
        // effectivement retourné les fonds. Si une autre direction (callback
        // racing) a déjà transitionné la ligne en RJCR, on ne touche pas.
        // On capture aussi son id pour lier le PiReturnExecution ci-dessous.
        CodeRaisonRetourFonds finalRaisonRetour = raisonRetour;
        Long returnRequestLocalId = returnRequestRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .map(req -> {
                    if (req.getStatut() == ReturnRequestStatus.PENDING) {
                        req.setStatut(ReturnRequestStatus.ACCEPTED);
                        req.setMsgIdRejet(msgId);
                        req.setRaisonRejet(finalRaisonRetour);
                        returnRequestRepository.save(req);
                        log.info("Demande de retour {} → ACCEPTED via PACS.004 INBOUND",
                                endToEndId);
                    }
                    return req.getId();
                })
                .orElse(null);

        // Audit trail : persister la PiReturnExecution INBOUND. Cette ligne
        // matérialise le retour effectif côté payeur (fonds re-crédités).
        // C'est aussi elle que le scénario B1 (auto-rejet ARDT) consulte via
        // findByEndToEndId pour détecter qu'un retour a déjà été exécuté —
        // cf. ReturnFundsCallbackController.receiveReturnRequest.
        // Le returnRequestId capturé ci-dessus garantit qu'on peut tracer
        // de la PiReturnRequest OUTBOUND vers le PACS.004 INBOUND final.
        PiReturnExecution execution = PiReturnExecution.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .montantRetourne(montantRetourne)
                .raisonRetour(raisonRetour)
                .returnRequestId(returnRequestLocalId)
                .build();
        returnExecutionRepository.save(execution);


        transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .ifPresent(transfer -> {
                    if (transfer.getStatut() != null  && transfer.getStatut() == TransferStatus.RTND) {
                        log.warn("PACS.004 ignoré pour transfer en déjà retourner "
                                        + "[endToEndId={}, statut={}] — pas d'écrasement",
                                endToEndId, transfer.getStatut());
                        return;
                    }
                    TransferStatus previous = transfer.getStatut();
                    transfer.setStatut(TransferStatus.RTND);
                    if (raisonRetourRaw != null && !raisonRetourRaw.isBlank()) {
                        transfer.setCodeRaison(raisonRetourRaw);
                    }
                    transferRepository.save(transfer);
                    log.info("Transfer {} → RTND via PACS.004 INBOUND "
                                    + "[précédent={}, raisonRetour={}, montantRetourne={}]",
                            endToEndId, previous, raisonRetourRaw, montantRetourne);
                });

        webhookService.notify(WebhookEventType.RETURN_EXECUTED, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }
}
