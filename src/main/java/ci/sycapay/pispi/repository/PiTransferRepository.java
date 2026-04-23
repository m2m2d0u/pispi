package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PiTransferRepository extends JpaRepository<PiTransfer, Long> {

    Optional<PiTransfer> findByEndToEndIdAndDirection(String endToEndId, MessageDirection direction);

    Optional<PiTransfer> findByMsgId(String msgId);

    Page<PiTransfer> findByDirection(MessageDirection direction, Pageable pageable);

    Page<PiTransfer> findByDirectionAndStatut(MessageDirection direction, TransferStatus statut, Pageable pageable);

    boolean existsByEndToEndId(String endToEndId);

    /**
     * Due send_schedule rows the Phase 3.4b runner should execute today.
     *
     * <p>Matches: {@code action = 'SEND_SCHEDULE' AND active = true AND
     * next_execution_date <= :today}. Natively indexed by the
     * {@code idx_pi_transfer_schedule_due} partial index from V25, so the
     * scan stays cheap even as the history of emitted transfers grows.
     */
    @Query(value =
            "SELECT * FROM pi_transfer " +
            "WHERE action = 'SEND_SCHEDULE' " +
            "  AND active = TRUE " +
            "  AND next_execution_date IS NOT NULL " +
            "  AND next_execution_date <= :today " +
            "ORDER BY next_execution_date ASC, id ASC",
            nativeQuery = true)
    List<PiTransfer> findDueSchedules(@Param("today") LocalDate today);
}
