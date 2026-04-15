package ci.sycapay.pispi.service.notification;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.notification.NotificationDto;
import ci.sycapay.pispi.entity.PiNotification;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.repository.PiNotificationRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.DateTimeUtil;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final PiNotificationRepository repository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    @Transactional
    public NotificationDto sendPing() {
        return sendNotification("PING", "Connectivity test", "/notifications/test-connectivite");
    }

    @Transactional
    public NotificationDto sendMain() {
        return sendNotification("MAIN", "Maintenance notification", "/notifications/maintenance");
    }

    private NotificationDto sendNotification(String evenement, String description, String aipPath) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> admi004 = new HashMap<>();
        admi004.put("msgId", msgId);
        admi004.put("evenement", evenement);
        admi004.put("evenementDescription", description);
        admi004.put("evenementDate", DateTimeUtil.nowIso());

        messageLogService.log(msgId, null, IsoMessageType.ADMI_004, MessageDirection.OUTBOUND, admi004, null, null);
        aipClient.post(aipPath, admi004);

        PiNotification notification = PiNotification.builder()
                .msgId(msgId)
                .direction(MessageDirection.OUTBOUND)
                .evenement(evenement)
                .evenementDescription(description)
                .evenementDate(LocalDateTime.now())
                .messageType(IsoMessageType.ADMI_004)
                .build();
        repository.save(notification);

        return toDto(notification);
    }

    public Page<NotificationDto> listNotifications(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    private NotificationDto toDto(PiNotification n) {
        return NotificationDto.builder()
                .msgId(n.getMsgId())
                .msgIdDemande(n.getMsgIdDemande())
                .direction(n.getDirection())
                .evenement(n.getEvenement())
                .evenementDescription(n.getEvenementDescription())
                .evenementDate(DateTimeUtil.formatDateTime(n.getEvenementDate()))
                .messageType(n.getMessageType())
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().toString() : null)
                .build();
    }
}
