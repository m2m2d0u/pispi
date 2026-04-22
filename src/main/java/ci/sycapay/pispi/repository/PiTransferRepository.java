package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiTransferRepository extends JpaRepository<PiTransfer, Long> {

    Optional<PiTransfer> findByEndToEndIdAndDirection(String endToEndId, MessageDirection direction);

    Optional<PiTransfer> findByMsgId(String msgId);

    Page<PiTransfer> findByDirection(MessageDirection direction, Pageable pageable);

    Page<PiTransfer> findByDirectionAndStatut(MessageDirection direction, TransferStatus statut, Pageable pageable);

    boolean existsByEndToEndId(String endToEndId);
}
