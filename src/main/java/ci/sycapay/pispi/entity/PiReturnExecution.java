package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.MessageDirection;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_return_execution")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiReturnExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", nullable = false, length = 35)
    private String msgId;

    @Column(name = "end_to_end_id", nullable = false, length = 35)
    private String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @Column(name = "montant_retourne", precision = 18, scale = 2)
    private BigDecimal montantRetourne;

    @Column(name = "raison_retour", length = 10)
    private String raisonRetour;

    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
