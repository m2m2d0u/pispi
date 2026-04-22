package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.TypeAlias;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PiAliasRepository extends JpaRepository<PiAlias, Long> {

    Optional<PiAlias> findByAliasValueAndTypeAliasAndStatut(
            String aliasValue, TypeAlias typeAlias, AliasStatus statut);

    Optional<PiAlias> findByAliasValueAndTypeAliasAndStatutIn(
            String aliasValue, TypeAlias typeAlias, List<AliasStatus> statuts);

    /**
     * MCOD uniqueness is scoped <b>per participant</b> per BCEAO PI-RAC §2.1.3.
     * Use this variant instead of the participant-agnostic lookup when checking
     * MCOD duplicates.
     */
    Optional<PiAlias> findByAliasValueAndTypeAliasAndCodeMembreParticipantAndStatutIn(
            String aliasValue, TypeAlias typeAlias, String codeMembreParticipant,
            List<AliasStatus> statuts);

    Page<PiAlias> findByCodeMembreParticipantAndStatut(
            String codeMembre, AliasStatus statut, Pageable pageable);

    Optional<PiAlias> findByEndToEndId(String endToEndId);

    List<PiAlias> findAllByEndToEndId(String endToEndId);

    Optional<PiAlias> findByEndToEndIdAndTypeAlias(String endToEndId, TypeAlias typeAlias);

    Optional<PiAlias> findByAliasValue(String aliasValue);

    Optional<PiAlias> findByCodification(String codification);

    // ----------------------------------------------------------------------
    // BCEAO §4.1 compliant lookups — use the opaque back-office client ID to
    // correlate a client's alias family (MBNO+SHID cascade, modification
    // fan-out, duplicate detection). Never expose PII outside the back office.
    // ----------------------------------------------------------------------

    /** Duplicate check for same client + same alias type (MBNO, SHID, MCOD). */
    Optional<PiAlias> findByBackOfficeClientIdAndTypeAliasAndStatutIn(
            String backOfficeClientId, TypeAlias typeAlias, List<AliasStatus> statuts);

    /** MBNO+SHID cascade — every ACTIVE alias owned by the same back-office client. */
    List<PiAlias> findAllByBackOfficeClientIdAndStatut(
            String backOfficeClientId, AliasStatus statut);

    long countByCodeMembreParticipantAndCreatedAtBetween(
            String codeMembre, LocalDateTime from, LocalDateTime to);

    /**
     * Finds alias rows stuck in a given status since before the cutoff — used
     * by the PENDING timeout scheduler to flip PENDING → FAILED when PI-RAC
     * never calls back.
     */
    List<PiAlias> findByStatutAndCreatedAtLessThan(AliasStatus statut, LocalDateTime cutoff);
}
