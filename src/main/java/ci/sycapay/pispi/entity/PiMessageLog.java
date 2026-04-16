package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "pi_message_log")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", unique = true, length = 35)
    private String msgId;

    @Column(name = "end_to_end_id", length = 50)
    private String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private IsoMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
