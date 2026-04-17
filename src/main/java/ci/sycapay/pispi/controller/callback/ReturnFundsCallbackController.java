package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.entity.PiReturnExecution;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiReturnExecutionRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ci.sycapay.pispi.dto.callback.RetourFondsCallbackPayload;
import ci.sycapay.pispi.dto.callback.RetourFondsDemandeCallbackPayload;
import ci.sycapay.pispi.dto.callback.RetourFondsRejetCallbackPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "Return Funds Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReturnFundsCallbackController {

    private final MessageLogService messageLogService;
    private final PiReturnRequestRepository returnRequestRepository;
    private final PiReturnExecutionRepository returnExecutionRepository;
    private final WebhookService webhookService;

    @Operation(summary = "Receive inbound return-of-funds request (CAMT.056)", description = "Called by the AIP when another participant requests a return of funds for a transfer they sent to this PI. Saves the request locally and fires a RETURN_REQUEST_RECEIVED webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetourFondsDemandeCallbackPayload.class)))
    @PostMapping("/retour-fonds/demande")
    public ResponseEntity<Void> receiveReturnRequest(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String identifiantDemande = (String) payload.get("identifiantDemandeRetourFonds");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.CAMT_056, MessageDirection.INBOUND, payload, 202, null);

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
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive return rejection (CAMT.029)", description = "Called by the AIP when the receiving PI rejects a return-of-funds request initiated by this PI. Updates local return request status to RJCR.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetourFondsRejetCallbackPayload.class)))
    @PostMapping("/retour-fonds/reponses")
    public ResponseEntity<Void> receiveReturnRejection(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String identifiantDemande = (String) payload.get("identifiantDemandeRetourFonds");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, null, IsoMessageType.CAMT_029, MessageDirection.INBOUND, payload, 202, null);

        returnRequestRepository.findByIdentifiantDemande(identifiantDemande).ifPresent(req -> {
            req.setStatut(ReturnRequestStatus.RJCR);
            req.setRaisonRejet(CodeRaisonRejetDemandeRetourFonds.valueOf((String) payload.get("raison")));
            req.setMsgIdRejet(msgId);
            returnRequestRepository.save(req);
        });

        webhookService.notify(WebhookEventType.RETURN_RESULT, null, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive return execution (PACS.004)", description = "Called by the AIP when the receiving PI accepts a return and transfers funds back. Saves the return execution record locally and fires a RETURN_EXECUTED webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetourFondsCallbackPayload.class)))
    @PostMapping("/retour-fonds")
    public ResponseEntity<Void> receiveReturnExecution(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_004, MessageDirection.INBOUND, payload, 202, null);

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
        return ResponseEntity.accepted().build();
    }
}
