package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiNotificationRepository extends JpaRepository<PiNotification, Long> {

    Page<PiNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
