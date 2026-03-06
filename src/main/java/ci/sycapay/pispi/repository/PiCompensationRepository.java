package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiCompensation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiCompensationRepository extends JpaRepository<PiCompensation, Long> {

    Page<PiCompensation> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
