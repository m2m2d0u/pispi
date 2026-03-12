package ci.sycapay.pispi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_invoice")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", length = 35)
    private String msgId;

    @Column(name = "groupe_id", length = 28)
    private String groupeId;

    @Column(name = "statement_id", length = 28)
    private String statementId;

    @Column(name = "sender_name", length = 140)
    private String senderName;

    @Column(name = "sender_id", length = 35)
    private String senderId;

    @Column(name = "receiver_name", length = 140)
    private String receiverName;

    @Column(name = "receiver_id", length = 35)
    private String receiverId;

    @Column(name = "date_debut_facture")
    private LocalDate dateDebutFacture;

    @Column(name = "date_fin_facture")
    private LocalDate dateFinFacture;

    @Column(name = "date_creation")
    private LocalDate dateCreation;

    @Column(name = "devise_compte", length = 3)
    private String deviseCompte;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_lines", columnDefinition = "jsonb")
    private String serviceLines;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
