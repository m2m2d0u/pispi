package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.service.alias.AliasCallbackService;
import ci.sycapay.pispi.enums.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final AliasCallbackService aliasCallbackService;

    // ---- Notification failures ----

    @Operation(summary = "Receive notification failure")
    @PostMapping("/notifications/echecs")
    public ResponseEntity<ApiResponse<Void>> receiveNotificationFailure(@RequestBody Map<String, Object> payload) {
        log.warn("Notification failure received: {}", payload);
        messageLogService.log(null, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    // ---- Verification failures ----

    @Operation(summary = "Receive verification failure")
    @PostMapping("/verifications-identites/echecs")
    public ResponseEntity<ApiResponse<Void>> receiveVerificationFailure(@RequestBody Map<String, Object> payload) {
        log.warn("Verification failure received: {}", payload);
        messageLogService.log(null, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    // ---- Participant list response ----

    @Operation(summary = "Receive participant list response")
    @PostMapping("/participants/liste/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveParticipantList(@RequestBody Map<String, Object> payload) {
        log.info("Participant list received: {}", payload);
        messageLogService.log(null, null, IsoMessageType.REDA_014, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    // ---- Alias responses ----

    @Operation(summary = "Receive alias search response")
    @PostMapping("/alias/recherche/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasSearchResponse(@RequestBody Map<String, Object> payload) {
        String endToEndId = (String) payload.get("endToEndId");
        log.info("Alias search response received: {}", payload);
        messageLogService.log(null, endToEndId, IsoMessageType.RAC_SEARCH, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update or create alias with data from PI-RAC
        aliasCallbackService.processSearchResponse(payload);

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive alias creation response")
    @PostMapping("/alias/creation/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasCreationResponse(@RequestBody Map<String, Object> payload) {
        String idCreationAlias = (String) payload.get("idCreationAlias");
        log.info("Alias creation response received: {}", payload);
        if (idCreationAlias != null && messageLogService.isDuplicate(idCreationAlias)) {
            return ResponseEntity.accepted().build();
        }
        messageLogService.log(null, idCreationAlias, IsoMessageType.RAC_CREATE, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update alias with actual values from PI-RAC
        aliasCallbackService.processCreationResponse(payload);

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive alias modification response")
    @PostMapping("/alias/modification/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasModificationResponse(@RequestBody Map<String, Object> payload) {
        String alias = (String) payload.get("alias");
        log.info("Alias modification response received: {}", payload);
        if (alias != null && messageLogService.isDuplicate("MOD_" + alias)) {
            return ResponseEntity.accepted().build();
        }
        messageLogService.log(null, "MOD_" + alias, IsoMessageType.RAC_MODIFY, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update alias with dateModification from PI-RAC
        aliasCallbackService.processModificationResponse(payload);

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive alias deletion response")
    @PostMapping("/alias/suppression/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasDeletionResponse(@RequestBody Map<String, Object> payload) {
        String alias = (String) payload.get("alias");
        log.info("Alias deletion response received: {}", payload);
        if (alias != null && messageLogService.isDuplicate("DEL_" + alias)) {
            return ResponseEntity.accepted().build();
        }
        messageLogService.log(null, "DEL_" + alias, IsoMessageType.RAC_DELETE, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update alias status to DELETED and set dateSuppressionRac
        aliasCallbackService.processDeletionResponse(payload);

        return ResponseEntity.accepted().build();
    }

    // ---- Revendication responses ----

    @Operation(summary = "Receive claim response")
    @PostMapping("/revendications/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimResponse(@RequestBody Map<String, Object> payload) {
        log.info("Claim response received: {}", payload);
        messageLogService.log(null, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive claim recovery response")
    @PostMapping("/revendications/recuperation/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimRecoveryResponse(@RequestBody Map<String, Object> payload) {
        log.info("Claim recovery response received: {}", payload);
        messageLogService.log(null, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive claim acceptance response")
    @PostMapping("/revendications/acceptation/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimAcceptanceResponse(@RequestBody Map<String, Object> payload) {
        log.info("Claim acceptance response received: {}", payload);
        messageLogService.log(null, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive claim rejection response")
    @PostMapping("/revendications/rejet/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimRejectionResponse(@RequestBody Map<String, Object> payload) {
        log.info("Claim rejection response received: {}", payload);
        messageLogService.log(null, null, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    // ---- Error callbacks ----

    @Operation(summary = "Receive HTTP send failure notification")
    @PostMapping("/message-envoi/echec-http")
    public ResponseEntity<ApiResponse<Void>> receiveHttpSendFailure(@RequestBody Map<String, Object> payload) {
        log.error("HTTP send failure: {}", payload);
        messageLogService.log(null, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive message processing failure")
    @PostMapping("/message-traitement/echec")
    public ResponseEntity<ApiResponse<Void>> receiveProcessingFailure(@RequestBody Map<String, Object> payload) {
        log.error("Message processing failure: {}", payload);
        messageLogService.log(null, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive ISO message send failure")
    @PostMapping("/messages-iso/echec-envoi")
    public ResponseEntity<ApiResponse<Void>> receiveIsoSendFailure(@RequestBody Map<String, Object> payload) {
        log.error("ISO message send failure: {}", payload);
        messageLogService.log(null, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

}
