package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.MessageDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiRequestToPayRepository extends JpaRepository<PiRequestToPay, Long> {

    Optional<PiRequestToPay> findByEndToEndId(String endToEndId);

    Page<PiRequestToPay> findByDirection(MessageDirection direction, Pageable pageable);
}
