package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.enums.MessageDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiReturnRequestRepository extends JpaRepository<PiReturnRequest, Long> {

    Optional<PiReturnRequest> findByIdentifiantDemande(String identifiantDemande);

    Optional<PiReturnRequest> findByEndToEndId(String endToEndId);

    Optional<PiReturnRequest> findByEndToEndIdAndDirection(String endToEndId, MessageDirection direction);
}
