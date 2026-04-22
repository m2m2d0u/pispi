package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiAliasRevendication;
import ci.sycapay.pispi.enums.StatutRevendication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PiAliasRevendicationRepository extends JpaRepository<PiAliasRevendication, Long> {

    Optional<PiAliasRevendication> findByIdentifiantRevendication(String identifiantRevendication);

    Optional<PiAliasRevendication> findByAliasValue(String aliasValue);

    /**
     * Scheduler query: claims still {@code INITIEE} whose {@code dateInitiation}
     * is older than the given cutoff and which have not yet been locked.
     * Drives the day-7 verrouillage job (§3.2).
     */
    List<PiAliasRevendication> findByStatutAndVerrouilleFalseAndDateInitiationLessThan(
            StatutRevendication statut, LocalDateTime cutoff);

    /**
     * Scheduler query: claims still {@code INITIEE} whose {@code dateInitiation}
     * is older than the given cutoff. Drives the day-14 auto-accept job (§3.3).
     */
    List<PiAliasRevendication> findByStatutAndDateInitiationLessThan(
            StatutRevendication statut, LocalDateTime cutoff);
}
