package ci.sycapay.pispi.service;

import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.repository.PiMessageLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogService {

    private final PiMessageLogRepository repository;
    private final ObjectMapper objectMapper;

    public PiMessageLog log(String msgId, String endToEndId, IsoMessageType messageType,
                            MessageDirection direction, Object payload, Integer httpStatus, String errorMessage) {
        String payloadJson = null;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize payload for msgId {}: {}", msgId, e.getMessage());
        }

        PiMessageLog entry = PiMessageLog.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .messageType(messageType)
                .direction(direction)
                .payload(payloadJson)
                .httpStatus(httpStatus)
                .errorMessage(errorMessage)
                .build();
        return repository.save(entry);
    }

    public boolean isDuplicate(String msgId) {
        return repository.existsByMsgId(msgId);
    }
}
