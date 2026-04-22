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

    Optional<PiAlias> findByAliasValueAndTypeAliasAndStatut(String aliasValue, TypeAlias typeAlias, AliasStatus statut);

    Optional<PiAlias> findByAliasValueAndTypeAliasAndStatutIn(String aliasValue, TypeAlias typeAlias, List<AliasStatus> statuts);

    /**
     * MCOD uniqueness is scoped <b>per participant</b> per BCEAO PI-RAC §2.1.3.
     * Use this variant instead of the participant-agnostic lookup when checking
     * MCOD duplicates.
     */
    Optional<PiAlias> findByAliasValueAndTypeAliasAndCodeMembreParticipantAndStatutIn(
            String aliasValue, TypeAlias typeAlias, String codeMembreParticipant, List<AliasStatus> statuts);

    Page<PiAlias> findByCodeMembreParticipantAndStatut(String codeMembre, AliasStatus statut, Pageable pageable);

    Optional<PiAlias> findByEndToEndId(String endToEndId);

    List<PiAlias> findAllByEndToEndId(String endToEndId);

    Optional<PiAlias> findByEndToEndIdAndTypeAlias(String endToEndId, TypeAlias typeAlias);

    Optional<PiAlias> findByIdentifiant(String identifiant);

    Optional<PiAlias> findByIdentifiantAndTypeAlias(String identifiant, TypeAlias typeAlias);

    Optional<PiAlias> findByIdentifiantAndTypeAliasAndStatutIn(
            String identifiant, TypeAlias typeAlias, List<AliasStatus> statuts);

    List<PiAlias> findAllByIdentifiantAndStatut(String identifiant, AliasStatus statut);

    Optional<PiAlias> findByAliasValue(String aliasValue);

    Optional<PiAlias> findByCodification(String codification);

    long countByCodeMembreParticipantAndCreatedAtBetween(
            String codeMembre, LocalDateTime from, LocalDateTime to);

    /**
     * Finds alias rows stuck in a given status since before the cutoff — used
     * by the PENDING timeout scheduler to flip PENDING → FAILED when PI-RAC
     * never calls back.
     */
    List<PiAlias> findByStatutAndCreatedAtLessThan(AliasStatus statut, LocalDateTime cutoff);
}
