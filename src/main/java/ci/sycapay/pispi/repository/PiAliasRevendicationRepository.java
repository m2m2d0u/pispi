package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiAliasRevendication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiAliasRevendicationRepository extends JpaRepository<PiAliasRevendication, Long> {

    Optional<PiAliasRevendication> findByIdentifiantRevendication(String identifiantRevendication);

    Optional<PiAliasRevendication> findByAliasValue(String aliasValue);
}
