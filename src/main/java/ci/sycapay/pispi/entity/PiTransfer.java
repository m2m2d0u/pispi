package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_transfer")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@ToString
public class PiTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", nullable = false, length = 35)
    private String msgId;

    @Column(name = "end_to_end_id", unique = true, nullable = false, length = 35)
    private String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_transaction", length = 10)
    private TypeTransaction typeTransaction;

    @Column(name = "canal_communication", length = 10)
    private CanalCommunication canalCommunication;

    @Column(name = "montant", precision = 18, scale = 2)
    private BigDecimal montant;

    @Column(name = "devise", length = 3)
    private String devise;

    @Column(name = "code_membre_payeur", length = 6)
    private String codeMembrePayeur;

    @Column(name = "numero_compte_payeur", length = 34)
    private String numeroComptePayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_payeur", length = 10)
    private TypeCompte typeComptePayeur;

    @Column(name = "nom_client_payeur", length = 140)
    private String nomClientPayeur;

    @Column(name = "prenom_client_payeur", length = 140)
    private String prenomClientPayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client_payeur", length = 5)
    private TypeClient typeClientPayeur;

    @Column(name = "telephone_payeur", length = 20)
    private String telephonePayeur;

    @Column(name = "code_membre_paye", length = 6)
    private String codeMembrePaye;

    @Column(name = "numero_compte_paye", length = 34)
    private String numeroComptePaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_paye", length = 10)
    private TypeCompte typeComptePaye;

    @Column(name = "nom_client_paye", length = 140)
    private String nomClientPaye;

    @Column(name = "prenom_client_paye", length = 140)
    private String prenomClientPaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client_paye", length = 5)
    private TypeClient typeClientPaye;

    @Column(name = "telephone_paye", length = 20)
    private String telephonePaye;

    @Column(name = "motif", length = 140)
    private String motif;

    @Column(name = "reference_client", length = 35)
    private String referenceClient;

    @Column(name = "montant_achat", precision = 18, scale = 2)
    private BigDecimal montantAchat;

    @Column(name = "montant_retrait", precision = 18, scale = 2)
    private BigDecimal montantRetrait;

    @Column(name = "frais_retrait", precision = 18, scale = 2)
    private BigDecimal fraisRetrait;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private TransferStatus statut;

    @Column(name = "code_raison", length = 10)
    private String codeRaison;

    @Column(name = "msg_id_reponse", length = 35)
    private String msgIdReponse;

    @Column(name = "date_heure_execution", nullable = false)
    private LocalDateTime dateHeureExecution;

    @Column(name = "date_heure_irrevocabilite")
    private LocalDateTime dateHeureIrrevocabilite;

    @Column(name = "rtp_end_to_end_id", length = 35)
    private String rtpEndToEndId;

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
