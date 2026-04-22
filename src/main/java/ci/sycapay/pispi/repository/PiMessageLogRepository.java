package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiMessageLogRepository extends JpaRepository<PiMessageLog, Long> {

    /**
     * Direction-aware msgId lookup. Since V23 the {@code (msg_id, direction)}
     * pair is the composite unique key — the same msgId can legitimately
     * appear in both the OUTBOUND row we wrote when sending a message and
     * in the INBOUND row we wrote when the AIP echoed the same msgId on an
     * admi.002 rejection. Always qualify a msgId lookup by direction to
     * avoid {@code NonUniqueResultException}.
     */
    Optional<PiMessageLog> findPiMessageLogByMsgIdAndDirectionIs(
            String msgId, MessageDirection direction);

    Optional<PiMessageLog> findFirstByEndToEndIdAndDirectionAndMessageTypeOrderByCreatedAtDesc(
            String endToEndId, MessageDirection direction, IsoMessageType messageType);
}
