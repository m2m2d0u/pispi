package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.entity.PiIdentityVerification;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiIdentityVerificationRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import ci.sycapay.pispi.dto.callback.VerificationCallbackPayload;
import ci.sycapay.pispi.dto.callback.VerificationResultatCallbackPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

@Tag(name = "Verification Callbacks")
@RestController
@RequiredArgsConstructor
public class VerificationCallbackController {

    private final MessageLogService messageLogService;
    private final PiIdentityVerificationRepository repository;
    private final WebhookService webhookService;

    @Operation(summary = "Receive inbound verification request (ACMT.023)", description = "Called by the AIP when another participant requests identity verification for an account held at this PI. Saves the request locally and fires a VERIFICATION_RECEIVED webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = VerificationCallbackPayload.class)))
    @PostMapping("/verifications-identites")
    public ApiResponse<Void> receiveVerification(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_023, MessageDirection.INBOUND, payload, 200, null);

        PiIdentityVerification verification = PiIdentityVerification.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .codeMembrePayeur((String) payload.get("codeMembreParticipantPayeur"))
                .codeMembrePaye((String) payload.get("codeMembreParticipantPaye"))
                .numeroComptePaye((String) payload.get("numeroCompteClientPaye"))
                .typeComptePaye(TypeCompte.valueOf((String) payload.get("typeCompteClientPaye")))
                .nomClientPaye((String) payload.get("nomClientPaye"))
                .prenomClientPaye((String) payload.get("prenomClientPaye"))
                .statut(VerificationStatus.PENDING)
                .build();
        repository.save(verification);

        webhookService.notify(WebhookEventType.VERIFICATION_RECEIVED, endToEndId, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive verification result (ACMT.024)", description = "Called by the AIP to deliver the result of a verification request this PI initiated. Updates local verification status and fires a VERIFICATION_RESULT webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = VerificationResultatCallbackPayload.class)))
    @PostMapping("/verifications-identites/reponses")
    public ApiResponse<Void> receiveVerificationResult(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
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
        return ApiResponse.ok("Callback received", null);
    }
}
