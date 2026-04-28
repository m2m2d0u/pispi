package ci.sycapay.pispi.service;

import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.repository.PiMessageLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        PiMessageLog saved = repository.save(entry);

        // Structured one-line summary at INFO. Full payload stays at DEBUG to
        // keep production INFO logs greppable. errorMessage when present is
        // promoted to WARN so failures jump out without scanning.
        emitStructuredLog(direction, messageType, endToEndId, msgId, httpStatus, errorMessage, payloadJson);
        return saved;
    }

    private void emitStructuredLog(MessageDirection direction, IsoMessageType messageType,
                                   String endToEndId, String msgId, Integer httpStatus,
                                   String errorMessage, String payloadJson) {
        String tag = direction == MessageDirection.INBOUND ? "INBOUND" : "OUTBOUND";
        if (errorMessage != null && !errorMessage.isBlank()) {
            log.warn("[{}] {} e2e={} msgId={} status={} error=\"{}\"",
                    tag, messageType, endToEndId, msgId, httpStatus, errorMessage);
        } else {
            log.info("[{}] {} e2e={} msgId={}{}",
                    tag, messageType, endToEndId, msgId,
                    httpStatus != null ? " status=" + httpStatus : "");
        }
        if (log.isDebugEnabled() && payloadJson != null) {
            log.debug("[{}] {} payload e2e={}: {}", tag, messageType, endToEndId, payloadJson);
        }
    }

    /** Saves the log entry in a new independent transaction so it commits even if the caller rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PiMessageLog logError(String endToEndId, IsoMessageType messageType, Object payload,
                                 Integer httpStatus, String errorMessage) {
        return log(null, endToEndId, messageType, MessageDirection.INBOUND, payload, httpStatus, errorMessage);
    }

    /**
     * Direction-aware idempotency check for callback paths. Returns true when
     * an INBOUND row already exists for this msgId — which is the only thing
     * callback controllers care about. Callers that used to rely on the older
     * {@code existsByMsgId} semantics see identical behaviour here because
     * every existing call site is in an inbound context.
     *
     * <p>Since V23 the (msg_id, direction) pair is the composite unique key
     * of {@code pi_message_log} — the same msgId can legitimately appear on
     * both sides (e.g. an outbound pain.013 we sent and the admi.002
     * rejection that echoes the same msgId). The direction filter prevents
     * false-positive dedup in that case.
     */
    public boolean isDuplicate(String msgId) {
        if (msgId == null) return false;
        return repository.findPiMessageLogByMsgIdAndDirectionIs(msgId, MessageDirection.INBOUND)
                .isPresent();
    }

    /** Alias of {@link #isDuplicate(String)} — kept for readability at call sites. */
    public boolean isDuplicateInbound(String msgId) {
        return isDuplicate(msgId);
    }
}
