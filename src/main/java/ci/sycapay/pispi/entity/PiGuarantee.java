package ci.sycapay.pispi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_guarantee")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiGuarantee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", length = 35)
    private String msgId;

    @Column(name = "source_message_type", length = 20)
    private String sourceMessageType;

    @Column(name = "participant_sponsor", length = 6)
    private String participantSponsor;

    @Column(name = "montant_garantie", precision = 18, scale = 2)
    private BigDecimal montantGarantie;

    @Column(name = "montant_restant_garantie", precision = 18, scale = 2)
    private BigDecimal montantRestantGarantie;

    @Column(name = "type_operation_garantie", length = 10)
    private String typeOperationGarantie;

    @Column(name = "date_effective_garantie")
    private String dateEffectiveGarantie;

    @Column(name = "montant_garantie_plafond", precision = 18, scale = 2)
    private BigDecimal montantGarantiePlafond;

    @Column(name = "date_debut")
    private String dateDebut;

    @Column(name = "date_fin")
    private String dateFin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
