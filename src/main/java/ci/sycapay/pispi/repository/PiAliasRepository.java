package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.TypeAlias;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PiAliasRepository extends JpaRepository<PiAlias, Long> {

    Optional<PiAlias> findByAliasValueAndTypeAliasAndStatut(String aliasValue, TypeAlias typeAlias, AliasStatus statut);

    Optional<PiAlias> findByAliasValueAndTypeAliasAndStatutIn(String aliasValue, TypeAlias typeAlias, List<AliasStatus> statuts);

    Page<PiAlias> findByCodeMembreParticipantAndStatut(String codeMembre, AliasStatus statut, Pageable pageable);

    Optional<PiAlias> findByEndToEndId(String endToEndId);

    Optional<PiAlias> findByIdentifiant(String identifiant);

    Optional<PiAlias> findByIdentifiantAndTypeAlias(String identifiant, TypeAlias typeAlias);
}
