package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_request_to_pay")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiRequestToPay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", nullable = false, length = 35)
    private String msgId;

    @Column(name = "end_to_end_id", unique = true, nullable = false, length = 35)
    private String endToEndId;

    @Column(name = "identifiant_demande_paiement", length = 35)
    private String identifiantDemandePaiement;

    @Column(name = "reference_bulk", length = 35)
    private String referenceBulk;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_transaction", length = 10)
    private TypeTransaction typeTransaction;

    @Column(name = "canal_communication", length = 10)
    private CanalCommunicationRtp canalCommunication;

    @Column(name = "montant", precision = 18, scale = 2)
    private BigDecimal montant;

    @Column(name = "devise", length = 3)
    private String devise;

    @Column(name = "date_heure_execution")
    private String dateHeureExecution;

    @Column(name = "date_heure_limite_action")
    private String dateHeureLimiteAction;

    @Column(name = "code_membre_payeur", length = 6)
    private String codeMembrePayeur;

    @Column(name = "numero_compte_payeur", length = 34)
    private String numeroComptePayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_payeur", length = 10)
    private TypeCompte typeComptePayeur;

    @Column(name = "nom_client_payeur", length = 140)
    private String nomClientPayeur;

    @Column(name = "telephone_payeur", length = 20)
    private String telephonePayeur;

    @Column(name = "code_membre_paye", length = 6)
    private String codeMembrePaye;

    @Column(name = "numero_compte_paye", length = 34)
    private String numeroComptePaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_paye", length = 10)
    private TypeCompteRtpPaye typeComptePaye;

    @Column(name = "nom_client_paye", length = 140)
    private String nomClientPaye;

    @Column(name = "telephone_paye", length = 20)
    private String telephonePaye;

    @Column(name = "motif", length = 140)
    private String motif;

    @Column(name = "autorisation_modification_montant")
    private Boolean autorisationModificationMontant;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private RtpStatus statut;

    @Column(name = "code_raison", length = 10)
    private String codeRaison;

    @Column(name = "msg_id_reponse", length = 35)
    private String msgIdReponse;

    @Column(name = "transfer_end_to_end_id", length = 35)
    private String transferEndToEndId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
