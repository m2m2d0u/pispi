package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiGuarantee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiGuaranteeRepository extends JpaRepository<PiGuarantee, Long> {

    Optional<PiGuarantee> findTopByOrderByCreatedAtDesc();
}
