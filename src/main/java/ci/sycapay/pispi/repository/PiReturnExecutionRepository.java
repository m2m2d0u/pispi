package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiReturnExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiReturnExecutionRepository extends JpaRepository<PiReturnExecution, Long> {

    /**
     * Détecte qu'un retour de fonds a déjà été exécuté pour un endToEndId,
     * peu importe sa direction (OUTBOUND = on a accepté + envoyé pacs.004
     * vers l'AIP, INBOUND = l'AIP nous a notifié l'exécution d'un retour
     * suite à notre camt.056).
     *
     * <p>Utilisé par {@code ReturnFundsCallbackController.receiveReturnRequest}
     * pour le scénario BCEAO §4.8 « transfert déjà retourné » qui doit
     * auto-rejeter avec raison {@code ARDT} sans notifier le client.
     */
    Optional<PiReturnExecution> findByEndToEndId(String endToEndId);
}
