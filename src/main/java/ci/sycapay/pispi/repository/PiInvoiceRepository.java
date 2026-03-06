package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiInvoiceRepository extends JpaRepository<PiInvoice, Long> {
}
