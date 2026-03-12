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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.parseDate;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Slf4j
@RestController
@RequestMapping("/api/pi/callback")
@RequiredArgsConstructor
public class NotificationCallbackController {

    private final MessageLogService messageLogService;
    private final PiNotificationRepository notificationRepository;
    private final PiGuaranteeRepository guaranteeRepository;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/notification")
    public ApiResponse<Void> receiveNotification(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.ADMI_004, MessageDirection.INBOUND, payload, 200, null);

        PiNotification notification = PiNotification.builder()
                .msgId(msgId)
                .direction(MessageDirection.INBOUND)
                .evenement((String) payload.get("evenement"))
                .evenementDescription((String) payload.get("evenementDescription"))
                .evenementDate((String) payload.get("evenementDate"))
                .messageType(IsoMessageType.ADMI_004)
                .build();
        notificationRepository.save(notification);

        webhookService.notify(WebhookEventType.PI_NOTIFICATION, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/notification/accuse")
    public ApiResponse<Void> receiveAcknowledgment(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.ADMI_011, MessageDirection.INBOUND, payload, 200, null);

        PiNotification notification = PiNotification.builder()
                .msgId(msgId)
                .msgIdDemande((String) payload.get("msgIdDemande"))
                .direction(MessageDirection.INBOUND)
                .evenement((String) payload.get("evenement"))
                .messageType(IsoMessageType.ADMI_011)
                .build();
        notificationRepository.save(notification);

        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/relation")
    public ApiResponse<Void> receiveRelation(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.REDA_017, MessageDirection.INBOUND, payload, 200, null);

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
        return ApiResponse.ok("Callback received", null);
    }

}
