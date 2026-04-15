package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.enums.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

@Tag(name = "Misc Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class MiscCallbackController {

    private final MessageLogService messageLogService;
    private final WebhookService webhookService;

    // ---- Notification failures ----

    @Operation(summary = "Receive notification failure")
    @PostMapping("/notifications/echecs")
    public ApiResponse<Void> receiveNotificationFailure(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.warn("Notification failure received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 200, null);
        webhookService.notify(WebhookEventType.MESSAGE_REJECTED, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    // ---- Verification failures ----

    @Operation(summary = "Receive verification failure")
    @PostMapping("/verifications-identites/echecs")
    public ApiResponse<Void> receiveVerificationFailure(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.warn("Verification failure received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 200, null);
        webhookService.notify(WebhookEventType.MESSAGE_REJECTED, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    // ---- Participant list response ----

    @Operation(summary = "Receive participant list response")
    @PostMapping("/participants/liste/reponses")
    public ApiResponse<Void> receiveParticipantList(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Participant list received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.REDA_014, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    // ---- Alias responses ----

    @Operation(summary = "Receive alias search response")
    @PostMapping("/alias/recherche/reponses")
    public ApiResponse<Void> receiveAliasSearchResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Alias search response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_SEARCH, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive alias creation response")
    @PostMapping("/alias/creation/reponses")
    public ApiResponse<Void> receiveAliasCreationResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Alias creation response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_CREATE, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive alias modification response")
    @PostMapping("/alias/modification/reponses")
    public ApiResponse<Void> receiveAliasModificationResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Alias modification response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_MODIFY, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive alias deletion response")
    @PostMapping("/alias/suppression/reponses")
    public ApiResponse<Void> receiveAliasDeletionResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Alias deletion response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_DELETE, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    // ---- Revendication responses ----

    @Operation(summary = "Receive claim response")
    @PostMapping("/revendications/reponses")
    public ApiResponse<Void> receiveClaimResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Claim response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive claim recovery response")
    @PostMapping("/revendications/recuperation/reponses")
    public ApiResponse<Void> receiveClaimRecoveryResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Claim recovery response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive claim acceptance response")
    @PostMapping("/revendications/acceptation/reponses")
    public ApiResponse<Void> receiveClaimAcceptanceResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Claim acceptance response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive claim rejection response")
    @PostMapping("/revendications/rejet/reponses")
    public ApiResponse<Void> receiveClaimRejectionResponse(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.info("Claim rejection response received: {}", msgId);
        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    // ---- Error callbacks ----

    @Operation(summary = "Receive HTTP send failure notification")
    @PostMapping("/message-envoi/echec-http")
    public ApiResponse<Void> receiveHttpSendFailure(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.error("HTTP send failure: {}", payload);
        if (msgId != null && messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        if (msgId != null) messageLogService.log(msgId, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive message processing failure")
    @PostMapping("/message-traitement/echec")
    public ApiResponse<Void> receiveProcessingFailure(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.error("Message processing failure: {}", payload);
        if (msgId != null && messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        if (msgId != null) messageLogService.log(msgId, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }

    @Operation(summary = "Receive ISO message send failure")
    @PostMapping("/messages-iso/echec-envoi")
    public ApiResponse<Void> receiveIsoSendFailure(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        log.error("ISO message send failure: {}", payload);
        if (msgId != null && messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        if (msgId != null) messageLogService.log(msgId, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 200, null);
        return ApiResponse.ok("Callback received", null);
    }
}
