package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiMessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiMessageLogRepository extends JpaRepository<PiMessageLog, Long> {

    Optional<PiMessageLog> findByMsgId(String msgId);

    boolean existsByMsgId(String msgId);
}
