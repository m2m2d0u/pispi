package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiIdentityVerificationRepository extends JpaRepository<PiIdentityVerification, Long> {

    Optional<PiIdentityVerification> findByEndToEndId(String endToEndId);
}
