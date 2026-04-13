package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "RTP Callbacks")
@Slf4j
@RestController
@RequestMapping("/api/pi/callback")
@RequiredArgsConstructor
public class RtpCallbackController {

    private final MessageLogService messageLogService;
    private final PiRequestToPayRepository rtpRepository;
    private final WebhookService webhookService;

    @PostMapping("/demande-paiement")
    public ApiResponse<Void> receiveRtp(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_013, MessageDirection.INBOUND, payload, 200, null);

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
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/demande-paiement/resultat")
    public ApiResponse<Void> receiveRtpResult(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_014, MessageDirection.INBOUND, payload, 200, null);

        rtpRepository.findByEndToEndId(endToEndId).ifPresent(rtp -> {
            rtp.setStatut(RtpStatus.RJCT);
            rtp.setCodeRaison((String) payload.get("codeRaison"));
            rtp.setMsgIdReponse(msgId);
            rtpRepository.save(rtp);
        });

        webhookService.notify(WebhookEventType.RTP_RESULT, endToEndId, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

}
