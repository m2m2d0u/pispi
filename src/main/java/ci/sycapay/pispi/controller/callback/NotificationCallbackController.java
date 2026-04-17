package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.entity.PiGuarantee;
import ci.sycapay.pispi.entity.PiNotification;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiGuaranteeRepository;
import ci.sycapay.pispi.repository.PiNotificationRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import ci.sycapay.pispi.dto.callback.AccuseCallbackPayload;
import ci.sycapay.pispi.dto.callback.NotificationCallbackPayload;
import ci.sycapay.pispi.dto.callback.RelationCallbackPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDate;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "Notification Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class NotificationCallbackController {

    private final MessageLogService messageLogService;
    private final PiNotificationRepository notificationRepository;
    private final PiGuaranteeRepository guaranteeRepository;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Receive system notification (ADMI.004)", description = "Called by the AIP to push a system event (e.g. connectivity test, maintenance notice). Saves the notification locally and fires a PI_NOTIFICATION webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = NotificationCallbackPayload.class)))
    @PostMapping("/notifications/info-warn")
    public ResponseEntity<ApiResponse<Void>> receiveNotification(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String evenement = (String) payload.get("evenement");
        log.info("ADMI.004 received [msgId={}, evenement={}]", msgId, evenement);

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().body(ApiResponse.accepted());
        messageLogService.log(msgId, null, IsoMessageType.ADMI_004, MessageDirection.INBOUND, payload, 202, null);

        PiNotification notification = PiNotification.builder()
                .msgId(msgId)
                .direction(MessageDirection.INBOUND)
                .evenement(evenement)
                .evenementDescription((String) payload.get("evenementDescription"))
                .evenementDate(parseDateTime(payload.get("evenementDate")))
                .messageType(IsoMessageType.ADMI_004)
                .build();
        notificationRepository.save(notification);

        webhookService.notify(WebhookEventType.PI_NOTIFICATION, null, msgId, payload);
        return ResponseEntity.accepted().body(ApiResponse.accepted());
    }

    @Operation(summary = "Receive acknowledgment (ADMI.011)", description = "Called by the AIP to acknowledge receipt of a notification previously sent by this PI. Saves the acknowledgment record locally.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = AccuseCallbackPayload.class)))
    @PostMapping("/notifications/accuse-reception")
    public ResponseEntity<ApiResponse<Void>> receiveAcknowledgment(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String msgIdDemande = (String) payload.get("msgIdDemande");
        String evenement = (String) payload.get("evenement");
        log.info("ADMI.011 received [msgId={}, msgIdDemande={}, evenement={}]", msgId, msgIdDemande, evenement);

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().body(ApiResponse.accepted());
        messageLogService.log(msgId, null, IsoMessageType.ADMI_011, MessageDirection.INBOUND, payload, 202, null);

        PiNotification notification = PiNotification.builder()
                .msgId(msgId)
                .msgIdDemande(msgIdDemande)
                .direction(MessageDirection.INBOUND)
                .evenement(evenement)
                .evenementDescription((String) payload.get("evenementDescription"))
                .evenementDate(payload.get("evenementDate") != null
                        ? parseDateTime(payload.get("evenementDate"))
                        : LocalDateTime.now())
                .messageType(IsoMessageType.ADMI_011)
                .build();
        notificationRepository.save(notification);

        webhookService.notify(WebhookEventType.NOTIFICATION_ACK, null, msgId, payload);
        return ResponseEntity.accepted().body(ApiResponse.accepted());
    }

    @Operation(summary = "Receive sponsor relation update (REDA.017)", description = "Called by the AIP to notify this PI of a change in the sponsor/guarantee relationship (ceiling amount, validity dates). Persists a PiGuarantee record with the new ceiling and fires a GUARANTEE_UPDATED webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RelationCallbackPayload.class)))
    @PostMapping("/notifications/relation")
    public ResponseEntity<ApiResponse<Void>> receiveRelation(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().body(ApiResponse.accepted());
        messageLogService.log(msgId, null, IsoMessageType.REDA_017, MessageDirection.INBOUND, payload, 202, null);

        try {
            PiGuarantee guarantee = PiGuarantee.builder()
                    .msgId(msgId)
                    .sourceMessageType(IsoMessageType.REDA_017)
                    .montantGarantiePlafond(payload.get("montantGarantiePlafond") != null ?
                            new BigDecimal(String.valueOf(payload.get("montantGarantiePlafond"))) : null)
                    .dateDebut(parseDate(payload.get("dateDebut")))
                    .dateFin(parseDate(payload.get("dateFin")))
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            guaranteeRepository.save(guarantee);
        } catch (Exception e) {
            log.error("Failed to persist REDA.017: {}", e.getMessage());
        }

        webhookService.notify(WebhookEventType.GUARANTEE_UPDATED, null, msgId, payload);
        return ResponseEntity.accepted().body(ApiResponse.accepted());
    }

}
