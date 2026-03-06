package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pi_notification")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", unique = true, nullable = false, length = 35)
    private String msgId;

    @Column(name = "msg_id_demande", length = 35)
    private String msgIdDemande;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @Column(name = "evenement", length = 10)
    private String evenement;

    @Column(name = "evenement_description")
    private String evenementDescription;

    @Column(name = "evenement_date")
    private String evenementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 20)
    private IsoMessageType messageType;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
