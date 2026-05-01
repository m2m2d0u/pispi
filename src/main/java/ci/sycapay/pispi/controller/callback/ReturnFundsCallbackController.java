package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.callback.RetourFondsCallbackPayload;
import ci.sycapay.pispi.dto.callback.RetourFondsDemandeCallbackPayload;
import ci.sycapay.pispi.dto.callback.RetourFondsRejetCallbackPayload;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiReturnExecutionRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

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

    @Operation(summary = "Receive inbound return-of-funds request (CAMT.056)", description = "Called by the AIP when another participant requests a return of funds for a transfer they sent to this PI. Saves the request locally and fires a RETURN_REQUEST_RECEIVED webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetourFondsDemandeCallbackPayload.class)))
    @PostMapping("/retour-fonds/demande")
    public ResponseEntity<Void> receiveReturnRequest(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("CAMT.056 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");
        String codeMembrePayeur = (String) payload.get("codeMembreParticipantPayeur");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.CAMT_056, MessageDirection.INBOUND, payload, 202, null);

        // Spec §4.8.1 inbound fields: msgId, codeMembreParticipantPayeur, endToEndId, raison.
        // identifiantDemandeRetourFonds is an internal AIP field not guaranteed in inbound payloads;
        // use msgId as our local identifier to satisfy the NOT NULL constraint.
        String identifiantDemande = (String) payload.get("identifiantDemandeRetourFonds");
        if (identifiantDemande == null || identifiantDemande.isBlank()) {
            identifiantDemande = msgId;
        }

        PiReturnRequest req = PiReturnRequest.builder()
                .msgId(msgId)
                .identifiantDemande(identifiantDemande)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .codeMembrePayeur(codeMembrePayeur)
                .raison(CodeRaisonDemandeRetourFonds.valueOf((String) payload.get("raison")))
                .statut(ReturnRequestStatus.PENDING)
                .build();
        returnRequestRepository.save(req);

        webhookService.notify(WebhookEventType.RETURN_REQUEST_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
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

        returnRequestRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND).ifPresent(req -> {
            req.setStatut(ReturnRequestStatus.RJCR);
            req.setRaisonRejet(CodeRaisonRetourFonds.valueOf((String) payload.get("raison")));
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
        CodeRaisonRetourFonds finalRaisonRetour = raisonRetour;
        returnRequestRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .ifPresent(req -> {
                    if (req.getStatut() == ReturnRequestStatus.PENDING) {
                        req.setStatut(ReturnRequestStatus.ACCEPTED);
                        req.setMsgIdRejet(msgId);
                        req.setRaisonRejet(finalRaisonRetour);
                        returnRequestRepository.save(req);
                        log.info("Demande de retour {} → ACCEPTED via PACS.004 INBOUND",
                                endToEndId);
                    }
                });


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
