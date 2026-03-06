package ci.sycapay.pispi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_transaction_report")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiTransactionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", length = 35)
    private String msgId;

    @Column(name = "identifiant_releve", length = 24)
    private String identifiantReleve;

    @Column(name = "date_debut_compense")
    private String dateDebutCompense;

    @Column(name = "date_fin_compense")
    private String dateFinCompense;

    @Column(name = "code_membre_participant", length = 6)
    private String codeMembreParticipant;

    @Column(name = "nbre_total_transaction")
    private Integer nbreTotalTransaction;

    @Column(name = "montant_total_compensation", precision = 18, scale = 2)
    private BigDecimal montantTotalCompensation;

    @Column(name = "indicateur_solde", length = 10)
    private String indicateurSolde;

    @Column(name = "page_courante")
    private Integer pageCourante;

    @Column(name = "derniere_page")
    private Boolean dernierePage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transactions", columnDefinition = "jsonb")
    private String transactions;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
