package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

import ci.sycapay.pispi.dto.callback.DemandePaiementCallbackPayload;
import ci.sycapay.pispi.dto.callback.DemandePaiementResultatCallbackPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "RTP Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class RtpCallbackController {

    private final MessageLogService messageLogService;
    private final PiRequestToPayRepository rtpRepository;
    private final WebhookService webhookService;

    @Operation(summary = "Receive inbound Request-to-Pay (PAIN.013)", description = "Called by the AIP when another participant sends a Request-to-Pay to this PI. Saves the RTP locally and fires an RTP_RECEIVED webhook so the backend can prompt the payer.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = DemandePaiementCallbackPayload.class)))
    @PostMapping("/demandes-paiements")
    public ResponseEntity<Void> receiveRtp(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_013, MessageDirection.INBOUND, payload, 202, null);

        PiRequestToPay rtp = PiRequestToPay.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .montant(payload.get("montant") != null ? new BigDecimal(String.valueOf(payload.get("montant"))) : null)
                .devise("XOF")
                .typeTransaction(TypeTransaction.valueOf((String) payload.get("typeTransaction")))
                .canalCommunication(CanalCommunicationRtp.fromCode((String) payload.get("canalCommunication")))
                .codeMembrePayeur((String) payload.get("codeMembreParticipantPayeur"))
                .codeMembrePaye((String) payload.get("codeMembreParticipantPaye"))
                .dateHeureLimiteAction(parseDateTime(payload.get("dateHeureLimiteAction")))
                .statut(RtpStatus.PENDING)
                .build();
        rtpRepository.save(rtp);

        webhookService.notify(WebhookEventType.RTP_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive RTP result (PAIN.014)", description = "Called by the AIP to deliver the payee's reject decision on a Request-to-Pay this PI initiated. Updates local RTP status and fires an RTP_RESULT webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = DemandePaiementResultatCallbackPayload.class)))
    @PostMapping("/demandes-paiements/reponses")
    public ResponseEntity<Void> receiveRtpResult(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_014, MessageDirection.INBOUND, payload, 202, null);

        rtpRepository.findByEndToEndId(endToEndId).ifPresent(rtp -> {
            rtp.setStatut(RtpStatus.RJCT);
            rtp.setCodeRaison((String) payload.get("codeRaison"));
            rtp.setMsgIdReponse(msgId);
            rtpRepository.save(rtp);
        });

        webhookService.notify(WebhookEventType.RTP_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }
}
