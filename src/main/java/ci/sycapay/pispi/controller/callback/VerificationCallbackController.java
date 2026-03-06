package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.entity.PiIdentityVerification;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiIdentityVerificationRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pi/callback")
@RequiredArgsConstructor
public class VerificationCallbackController {

    private final MessageLogService messageLogService;
    private final PiIdentityVerificationRepository repository;
    private final WebhookService webhookService;

    @PostMapping("/verification")
    public ResponseEntity<Void> receiveVerification(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.ok().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_023, MessageDirection.INBOUND, payload, 200, null);

        PiIdentityVerification verification = PiIdentityVerification.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .codeMembrePayeur((String) payload.get("codeMembreParticipantPayeur"))
                .codeMembrePaye((String) payload.get("codeMembreParticipantPaye"))
                .numeroComptePaye((String) payload.get("numeroCompteClientPaye"))
                .typeComptePaye((String) payload.get("typeCompteClientPaye"))
                .nomClientPaye((String) payload.get("nomClientPaye"))
                .prenomClientPaye((String) payload.get("prenomClientPaye"))
                .statut(VerificationStatus.PENDING)
                .build();
        repository.save(verification);

        webhookService.notify(WebhookEventType.VERIFICATION_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verification/resultat")
    public ResponseEntity<Void> receiveVerificationResult(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.ok().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_024, MessageDirection.INBOUND, payload, 200, null);

        repository.findByEndToEndId(endToEndId).ifPresent(v -> {
            Boolean result = (Boolean) payload.get("resultatVerification");
            v.setResultatVerification(result);
            v.setCodeRaison((String) payload.get("codeRaison"));
            v.setMsgIdReponse(msgId);
            v.setStatut(Boolean.TRUE.equals(result) ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
            repository.save(v);
        });

        webhookService.notify(WebhookEventType.VERIFICATION_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.ok().build();
    }
}
