package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiParticipantRepository extends JpaRepository<PiParticipant, Long> {

    Optional<PiParticipant> findByCodeMembre(String codeMembre);
}
