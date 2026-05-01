package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.service.callback.Admi002CallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

import ci.sycapay.pispi.dto.callback.RejetCallbackPayload;
import ci.sycapay.pispi.dto.callback.VirementCallbackPayload;
import ci.sycapay.pispi.dto.callback.VirementResultatCallbackPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;

import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "Transfer Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class TransferCallbackController {

    private final MessageLogService messageLogService;
    private final PiTransferRepository transferRepository;
    private final PiRequestToPayRepository rtpRepository;
    private final WebhookService webhookService;
    private final PiSpiProperties properties;
    private final Admi002CallbackService admi002CallbackService;

    @Operation(summary = "Receive inbound transfer (PACS.008)", description = "Called by the AIP when another participant sends a credit transfer to this PI. Saves the transfer locally and forwards a webhook event to the backend.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = VirementCallbackPayload.class)))
    @PostMapping("/transferts")
    public ResponseEntity<Void> receiveTransfer(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("PACS.008 received raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_008, MessageDirection.INBOUND, payload, 202, null);

        // If we already have an outbound row for this endToEndId it's an echo of our own DISP — no new record needed.
        if (transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND).isPresent()) {
            webhookService.notify(WebhookEventType.TRANSFER_RECEIVED, endToEndId, msgId, payload);
            return ResponseEntity.accepted().build();
        }

        String typeComptePayeurStr = (String) payload.get("typeCompteClientPayeur");
        String typeClientPayeurStr = (String) payload.get("typeClientPayeur");
        String typeComptePayeStr   = (String) payload.get("typeCompteClientPaye");
        String typeClientPayeStr   = (String) payload.get("typeClientPaye");
        // typeTransaction + canalCommunication are both optional on inbound
        // pacs.008 — BCEAO omits typeTransaction on most canals (it's only
        // mandated for "paiement programmé" per spec §4.3.1.1 p.69) and leaving
        // canalCommunication out is rare but not impossible for misc callbacks.
        // Guard both with null checks; otherwise TypeTransaction.valueOf(null)
        // blows up with NPE and kills the whole callback.
        String typeTransactionStr  = (String) payload.get("typeTransaction");
        String canalCommunicationStr = (String) payload.get("canalCommunication");

        PiTransfer transfer = PiTransfer.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .montant(payload.get("montant") != null ? new BigDecimal(String.valueOf(payload.get("montant"))) : null)
                .devise("XOF")
                .codeMembrePayeur((String) payload.get("codeMembreParticipantPayeur"))
                .codeMembrePaye(payload.getOrDefault("codeMembreParticipantPaye", properties.getCodeMembre()).toString())
                .typeTransaction(parseEnumLoose(typeTransactionStr, TypeTransaction.class, "typeTransaction"))
                .canalCommunication(parseCanalLoose(canalCommunicationStr))
                .nomClientPayeur((String) payload.get("nomClientPayeur"))
                .prenomClientPayeur((String) payload.get("prenomClientPayeur"))
                .typeClientPayeur(parseEnumLoose(typeClientPayeurStr, TypeClient.class, "typeClientPayeur"))
                .numeroComptePayeur((String) payload.get("otherClientPayeur"))
                .typeComptePayeur(parseEnumLoose(typeComptePayeurStr, TypeCompte.class, "typeComptePayeur"))
                .nomClientPaye((String) payload.get("nomClientPaye"))
                .prenomClientPaye((String) payload.get("prenomClientPaye"))
                .typeClientPaye(parseEnumLoose(typeClientPayeStr, TypeClient.class, "typeClientPaye"))
                .numeroComptePaye((String) payload.get("otherClientPaye"))
                .typeComptePaye(parseEnumLoose(typeComptePayeStr, TypeCompte.class, "typeComptePaye"))
                .motif((String) payload.get("motif"))
                .dateHeureExecution(parseDateTime(payload.get("dateHeureAcceptation")) != null
                        ? parseDateTime(payload.get("dateHeureAcceptation"))
                        : LocalDateTime.now())
                .statut(TransferStatus.PEND)
                .build();
        transferRepository.save(transfer);

        webhookService.notify(WebhookEventType.TRANSFER_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive transfer result (PACS.002)", description = "Called by the AIP to deliver the final accept/reject outcome of an outbound transfer. Updates local transfer status and forwards a webhook event.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = VirementResultatCallbackPayload.class)))
    @PostMapping("/transferts/reponses")
    public ResponseEntity<Void> receiveTransferResult(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("PACS.002 received raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_002, MessageDirection.INBOUND, payload, 202, null);

        // Le PACS.002 INBOUND peut concerner deux cas symétriques :
        //   1. OUTBOUND : on a émis un PACS.008 ; l'AIP nous renvoie le résultat
        //      du settlement (ACCC/ACSC/RJCT) côté destination.
        //   2. INBOUND  : on a reçu un PACS.008 et on a répondu ACSP via
        //      /accept ; l'AIP nous renvoie ensuite le résultat final
        //      (ACCC/ACSC après compensation, ou RJCT en cas d'échec).
        // On essaie OUTBOUND d'abord (cas le plus courant), puis INBOUND.
        transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .or(() -> transferRepository.findByEndToEndIdAndDirection(
                        endToEndId, MessageDirection.INBOUND))
                .ifPresent(transfer -> {
            // Garde terminale : un PACS.002 retardataire ne doit pas écraser
            // un statut déjà finalisé. On logue et on sort proprement.
            if (transfer.getStatut() != null && transfer.getStatut().isTerminal()) {
                log.warn("PACS.002 ignoré sur transfer en statut terminal [endToEndId={}, "
                        + "direction={}, statut={}, payload msgId={}]",
                        endToEndId, transfer.getDirection(), transfer.getStatut(), msgId);
                return;
            }

            // Parse défensif : un statutTransaction inconnu (typo, valeur future,
            // null) doit produire un log + 202 Accepted plutôt qu'une 500 qui
            // pousse l'AIP à re-déliverer indéfiniment.
            String statutRaw = (String) payload.get("statutTransaction");
            TransferStatus ts = parseEnumLoose(statutRaw, TransferStatus.class, "statutTransaction");
            if (ts == null) {
                log.error("PACS.002 avec statutTransaction inconnu [endToEndId={}, "
                        + "raw='{}'] — ligne locale laissée intacte", endToEndId, statutRaw);
                return;
            }

            transfer.setStatut(ts);
            transfer.setCodeRaison((String) payload.get("codeRaison"));
            transfer.setMsgIdReponse(msgId);
            transfer.setDateHeureIrrevocabilite(parseDateTime(payload.get("dateHeureIrrevocabilite")));
            transferRepository.save(transfer);
            log.info("PACS.002 INBOUND appliqué [endToEndId={}, direction={}, statut={} → {}]",
                    endToEndId, transfer.getDirection(), TransferStatus.PEND, ts);

            // Si ce PACS.002 finalise une acceptation RTP, faire avancer le RTP.
            // V44 : on utilise le lien explicite {@code transfer.rtpEndToEndId}
            // posé par {@code confirmRtpAcceptance}. Si l'absence de lien (cas
            // d'un transfer non-issu d'un RTP), on saute proprement — plus
            // d'heuristique findFirst + filtre PREVALIDATION.
            String rtpE2e = transfer.getRtpEndToEndId();
            if (rtpE2e != null) {
                rtpRepository.findByEndToEndIdAndDirection(rtpE2e, MessageDirection.INBOUND)
                        .or(() -> rtpRepository.findByEndToEndIdAndDirection(
                                rtpE2e, MessageDirection.OUTBOUND))
                        .ifPresent(rtp -> {
                            // Garde terminale aussi côté RTP.
                            if (rtp.getStatut() != null && rtp.getStatut().isTerminal()) {
                                log.warn("PACS.002 → RTP ignoré, RTP en statut terminal "
                                        + "[rtpEndToEndId={}, statut={}]",
                                        rtpE2e, rtp.getStatut());
                                return;
                            }
                            // ACSP est intermédiaire — n'arrête pas le RTP en ACCEPTED
                            // tant que l'AIP n'a pas envoyé ACCC/ACSC.
                            boolean accepted = ts == TransferStatus.ACCC
                                    || ts == TransferStatus.ACSC;
                            boolean rejected = ts == TransferStatus.RJCT;
                            if (accepted) {
                                rtp.setStatut(RtpStatus.ACCEPTED);
                            } else if (rejected) {
                                rtp.setStatut(RtpStatus.RJCT);
                                rtp.setCodeRaison((String) payload.get("codeRaison"));
                            }
                            // ACSP : on laisse le RTP en PREVALIDATION en attendant ACCC/ACSC.
                            if (accepted || rejected) {
                                rtpRepository.save(rtp);
                                log.info("RTP {} → {} via PACS.002 [transferStatut={}]",
                                        rtpE2e, rtp.getStatut(), ts);
                            }
                        });
            }
        });

        webhookService.notify(WebhookEventType.TRANSFER_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    // =======================================================================
    // Helpers — defensive parsing
    // =======================================================================

    /**
     * Convertit une string en enum sans jeter d'exception. Si la valeur est
     * null/blank, retourne null. Si la valeur est non-vide mais inconnue de
     * l'enum (typo, valeur future BCEAO), logue et retourne null — le caller
     * décide quoi faire (le PACS.002 handler abandonne, le PACS.008 inbound
     * accepte des champs nullable côté entité).
     */
    private static <E extends Enum<E>> E parseEnumLoose(String value, Class<E> cls, String fieldHint) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(cls, value);
        } catch (IllegalArgumentException e) {
            org.slf4j.LoggerFactory.getLogger(TransferCallbackController.class)
                    .warn("Valeur enum inconnue pour {} : '{}' (enum={}). Champ ignoré.",
                            fieldHint, value, cls.getSimpleName());
            return null;
        }
    }

    /** Variante pour {@link CanalCommunication} qui utilise {@code fromCode} (codes 3-digit) plutôt que {@code valueOf}. */
    private static CanalCommunication parseCanalLoose(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return CanalCommunication.fromCode(code);
        } catch (IllegalArgumentException e) {
            org.slf4j.LoggerFactory.getLogger(TransferCallbackController.class)
                    .warn("Canal inconnu reçu en callback : '{}' — champ ignoré.", code);
            return null;
        }
    }

    @Operation(summary = "Receive message rejection (ADMI.002)",
            description = "Called by the AIP when a previously submitted PACS.008 / PACS.002 / "
                    + "PACS.028 is structurally rejected. Delegates to Admi002CallbackService "
                    + "which marks the originating transfer as ECHEC, reverts any linked RTP "
                    + "from PREVALIDATION to PENDING (so the débiteur peut retenter avec des "
                    + "données corrigées), and fires a MESSAGE_REJECTED webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RejetCallbackPayload.class)))
    @PostMapping("/transferts/echecs")
    public ResponseEntity<Void> receiveRejection(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("ADMI.002 received raw payload: {}", payload);
        // PACS_008 hint pour que le service route directement vers
        // {@code applyToTransfer} sans détour par {@code pi_message_log}.
        admi002CallbackService.handleRejection(payload, IsoMessageType.PACS_008);
        return ResponseEntity.accepted().build();
    }
}
