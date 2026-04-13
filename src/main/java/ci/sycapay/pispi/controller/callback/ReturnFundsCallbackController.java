package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.entity.PiReturnExecution;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiReturnExecutionRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "Return Funds Callbacks")
@RestController
@RequestMapping("/api/pi/callback")
@RequiredArgsConstructor
public class ReturnFundsCallbackController {

    private final MessageLogService messageLogService;
    private final PiReturnRequestRepository returnRequestRepository;
    private final PiReturnExecutionRepository returnExecutionRepository;
    private final WebhookService webhookService;

    @PostMapping("/retour-fonds/demande")
    public ApiResponse<Void> receiveReturnRequest(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String identifiantDemande = (String) payload.get("identifiantDemandeRetourFonds");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, endToEndId, IsoMessageType.CAMT_056, MessageDirection.INBOUND, payload, 200, null);

        PiReturnRequest req = PiReturnRequest.builder()
                .msgId(msgId)
                .identifiantDemande(identifiantDemande)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .raison(CodeRaisonDemandeRetourFonds.valueOf((String) payload.get("raison")))
                .statut(ReturnRequestStatus.PENDING)
                .build();
        returnRequestRepository.save(req);

        webhookService.notify(WebhookEventType.RETURN_REQUEST_RECEIVED, endToEndId, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/retour-fonds/rejet")
    public ApiResponse<Void> receiveReturnRejection(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String identifiantDemande = (String) payload.get("identifiantDemandeRetourFonds");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.CAMT_029, MessageDirection.INBOUND, payload, 200, null);

        returnRequestRepository.findByIdentifiantDemande(identifiantDemande).ifPresent(req -> {
            req.setStatut(ReturnRequestStatus.RJCR);
            req.setRaisonRejet(CodeRaisonRejetDemandeRetourFonds.valueOf((String) payload.get("raison")));
            req.setMsgIdRejet(msgId);
            returnRequestRepository.save(req);
        });

        webhookService.notify(WebhookEventType.RETURN_RESULT, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/retour-fonds")
    public ApiResponse<Void> receiveReturnExecution(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_004, MessageDirection.INBOUND, payload, 200, null);

        PiReturnExecution execution = PiReturnExecution.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .montantRetourne(payload.get("montantRetourne") != null ?
                        new BigDecimal(String.valueOf(payload.get("montantRetourne"))) : null)
                .raisonRetour(CodeRaisonRetourFonds.valueOf((String) payload.get("raisonRetour")))
                .build();
        returnExecutionRepository.save(execution);

        webhookService.notify(WebhookEventType.RETURN_EXECUTED, endToEndId, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }
}
