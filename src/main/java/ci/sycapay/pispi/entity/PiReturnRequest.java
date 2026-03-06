package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.CodeRaisonDemandeRetourFonds;
import ci.sycapay.pispi.enums.CodeRaisonRejetDemandeRetourFonds;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.ReturnRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pi_return_request")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", nullable = false, length = 35)
    private String msgId;

    @Column(name = "identifiant_demande", unique = true, nullable = false, length = 35)
    private String identifiantDemande;

    @Column(name = "end_to_end_id", nullable = false, length = 35)
    private String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @Column(name = "code_membre_payeur", length = 6)
    private String codeMembrePayeur;

    @Column(name = "code_membre_paye", length = 6)
    private String codeMembrePaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "raison", length = 10)
    private CodeRaisonDemandeRetourFonds raison;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private ReturnRequestStatus statut;

    @Enumerated(EnumType.STRING)
    @Column(name = "raison_rejet", length = 10)
    private CodeRaisonRejetDemandeRetourFonds raisonRejet;

    @Column(name = "msg_id_rejet", length = 35)
    private String msgIdRejet;

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
