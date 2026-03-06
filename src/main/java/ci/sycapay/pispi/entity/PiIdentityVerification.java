package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pi_identity_verification")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiIdentityVerification {

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

    @Column(name = "code_membre_payeur", length = 6)
    private String codeMembrePayeur;

    @Column(name = "code_membre_paye", length = 6)
    private String codeMembrePaye;

    @Column(name = "numero_compte_paye", length = 34)
    private String numeroComptePaye;

    @Column(name = "type_compte_paye", length = 10)
    private String typeComptePaye;

    @Column(name = "nom_client_paye", length = 140)
    private String nomClientPaye;

    @Column(name = "prenom_client_paye", length = 140)
    private String prenomClientPaye;

    @Column(name = "resultat_verification")
    private Boolean resultatVerification;

    @Column(name = "code_raison", length = 10)
    private String codeRaison;

    @Column(name = "msg_id_reponse", length = 35)
    private String msgIdReponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private VerificationStatus statut;

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
