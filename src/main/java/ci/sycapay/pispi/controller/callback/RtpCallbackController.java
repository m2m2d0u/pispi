package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/pi/callback")
@RequiredArgsConstructor
public class RtpCallbackController {

    private final MessageLogService messageLogService;
    private final PiRequestToPayRepository rtpRepository;
    private final WebhookService webhookService;

    @PostMapping("/demande-paiement")
    public ResponseEntity<Void> receiveRtp(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.ok().build();
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
                .dateHeureLimiteAction((String) payload.get("dateHeureLimiteAction"))
                .statut(RtpStatus.PENDING)
                .build();
        rtpRepository.save(rtp);

        webhookService.notify(WebhookEventType.RTP_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/demande-paiement/resultat")
    public ResponseEntity<Void> receiveRtpResult(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.ok().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_014, MessageDirection.INBOUND, payload, 200, null);

        rtpRepository.findByEndToEndId(endToEndId).ifPresent(rtp -> {
            rtp.setStatut(RtpStatus.RJCT);
            rtp.setCodeRaison((String) payload.get("codeRaison"));
            rtp.setMsgIdReponse(msgId);
            rtpRepository.save(rtp);
        });

        webhookService.notify(WebhookEventType.RTP_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.ok().build();
    }
}
