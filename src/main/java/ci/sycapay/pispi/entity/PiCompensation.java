package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.IndicateurSolde;
import ci.sycapay.pispi.enums.TypeBalanceCompense;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_compensation")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiCompensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", length = 35)
    private String msgId;

    @Column(name = "solde_id", length = 50)
    private String soldeId;

    @Column(name = "date_debut_compense")
    private LocalDateTime dateDebutCompense;

    @Column(name = "date_fin_compense")
    private LocalDateTime dateFinCompense;

    @Column(name = "participant", length = 6)
    private String participant;

    @Column(name = "participant_sponsor", length = 6)
    private String participantSponsor;

    @Enumerated(EnumType.STRING)
    @Column(name = "balance_type", length = 10)
    private TypeBalanceCompense balanceType;

    @Column(name = "montant", precision = 18, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 10)
    private IndicateurSolde operationType;

    @Column(name = "date_balance")
    private LocalDateTime dateBalance;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
