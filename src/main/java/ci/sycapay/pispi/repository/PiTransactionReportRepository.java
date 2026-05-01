package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiTransactionReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiTransactionReportRepository extends JpaRepository<PiTransactionReport, Long> {

    /** Liste paginée triée par date de réception décroissante. */
    Page<PiTransactionReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Page d'un relevé multi-pages par identifiant — corrèle les
     * {@code PiTransactionReport} qui partagent le même {@code identifiantReleve}
     * (cf. BCEAO §4.11.2 « Le rapport peut être sur plusieurs pages »).
     */
    Page<PiTransactionReport> findByIdentifiantReleveOrderByPageCouranteAsc(
            String identifiantReleve, Pageable pageable);

    /** Première page du relevé identifié — utile pour pré-vérifier qu'un téléchargement a abouti. */
    Optional<PiTransactionReport> findFirstByIdentifiantReleveOrderByPageCouranteAsc(
            String identifiantReleve);
}
