package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiTransactionReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiTransactionReportRepository extends JpaRepository<PiTransactionReport, Long> {
}
