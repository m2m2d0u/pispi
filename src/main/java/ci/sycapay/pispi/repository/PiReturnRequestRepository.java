package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.ReturnRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiReturnRequestRepository extends JpaRepository<PiReturnRequest, Long> {

    Optional<PiReturnRequest> findByIdentifiantDemande(String identifiantDemande);

    Optional<PiReturnRequest> findByEndToEndId(String endToEndId);

    Optional<PiReturnRequest> findByEndToEndIdAndDirection(String endToEndId, MessageDirection direction);

    /**
     * Recherche une demande de retour de fonds non-encore-finalisée pour un
     * endToEndId donné. Utilisé pour deux contrôles BCEAO §4.8 :
     *
     * <ul>
     *   <li>Avant émission OUTBOUND : empêcher l'envoi d'un nouveau camt.056
     *       tant qu'une demande PENDING précédente n'a pas été tranchée
     *       (évite les doublons côté AIP).</li>
     *   <li>À réception INBOUND : idempotence — si une demande INBOUND
     *       existe déjà pour cet e2e, no-op.</li>
     * </ul>
     */
    Optional<PiReturnRequest> findFirstByEndToEndIdAndDirectionAndStatut(
            String endToEndId, MessageDirection direction, ReturnRequestStatus statut);
}
