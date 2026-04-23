package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
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
    private final WebhookService webhookService;
    private final PiSpiProperties properties;

    @Operation(summary = "Receive inbound transfer (PACS.008)", description = "Called by the AIP when another participant sends a credit transfer to this PI. Saves the transfer locally and forwards a webhook event to the backend.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = VirementCallbackPayload.class)))
    @PostMapping("/transferts")
    public ResponseEntity<Void> receiveTransfer(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.info("PACS.008 received: {}", payload);
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
                .typeTransaction(typeTransactionStr != null ? TypeTransaction.valueOf(typeTransactionStr) : null)
                .canalCommunication(canalCommunicationStr != null ? CanalCommunication.fromCode(canalCommunicationStr) : null)
                .nomClientPayeur((String) payload.get("nomClientPayeur"))
                .prenomClientPayeur((String) payload.get("prenomClientPayeur"))
                .typeClientPayeur(typeClientPayeurStr != null ? TypeClient.valueOf(typeClientPayeurStr) : null)
                .numeroComptePayeur((String) payload.get("otherClientPayeur"))
                .typeComptePayeur(typeComptePayeurStr != null ? TypeCompte.valueOf(typeComptePayeurStr) : null)
                .nomClientPaye((String) payload.get("nomClientPaye"))
                .prenomClientPaye((String) payload.get("prenomClientPaye"))
                .typeClientPaye(typeClientPayeStr != null ? TypeClient.valueOf(typeClientPayeStr) : null)
                .numeroComptePaye((String) payload.get("otherClientPaye"))
                .typeComptePaye(typeComptePayeStr != null ? TypeCompte.valueOf(typeComptePayeStr) : null)
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
        log.info("PACS.002 received: {}", payload);
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_002, MessageDirection.INBOUND, payload, 202, null);

        transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND).ifPresent(transfer -> {
            String statut = (String) payload.get("statutTransaction");
            transfer.setStatut(TransferStatus.valueOf(statut));
            transfer.setCodeRaison((String) payload.get("codeRaison"));
            transfer.setMsgIdReponse(msgId);
            transfer.setDateHeureIrrevocabilite(parseDateTime(payload.get("dateHeureIrrevocabilite")));
            transferRepository.save(transfer);
        });

        webhookService.notify(WebhookEventType.TRANSFER_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive message rejection (ADMI.002)", description = "Called by the AIP when a previously submitted message is structurally rejected. Logs the rejection and fires a MESSAGE_REJECTED webhook event.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RejetCallbackPayload.class)))
    @PostMapping("/transferts/echecs")
    public ResponseEntity<Void> receiveRejection(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.info("ADMI.002 received: {}", payload);
        String msgId = (String) payload.get("msgId");
        if (msgId == null) msgId = (String) payload.get("reference");

        log.warn("ADMI.002 rejection received [msgId={}]", msgId);
        if (msgId != null && messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        if (msgId != null) messageLogService.log(msgId, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 202, null);

        webhookService.notify(WebhookEventType.MESSAGE_REJECTED, null, msgId, payload);
        return ResponseEntity.accepted().build();
    }
}
