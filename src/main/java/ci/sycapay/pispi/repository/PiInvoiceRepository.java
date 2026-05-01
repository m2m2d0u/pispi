package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiInvoiceRepository extends JpaRepository<PiInvoice, Long> {

    /** Liste paginée triée par date de réception décroissante. */
    Page<PiInvoice> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
