package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiMessageLogRepository extends JpaRepository<PiMessageLog, Long> {

    Optional<PiMessageLog> findByMsgId(String msgId);

    boolean existsByMsgId(String msgId);

    Optional<PiMessageLog> findFirstByEndToEndIdAndDirectionAndMessageTypeOrderByCreatedAtDesc(
            String endToEndId, MessageDirection direction, IsoMessageType messageType);
}
