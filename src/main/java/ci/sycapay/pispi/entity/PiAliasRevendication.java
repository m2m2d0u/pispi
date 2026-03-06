package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.StatutRevendication;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pi_alias_revendication")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiAliasRevendication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identifiant_revendication", unique = true, nullable = false, length = 50)
    private String identifiantRevendication;

    @Column(name = "alias_value", nullable = false, length = 50)
    private String aliasValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @Column(name = "detenteur", length = 6)
    private String detenteur;

    @Column(name = "revendicateur", length = 6)
    private String revendicateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutRevendication statut;

    @Column(name = "date_action")
    private String dateAction;

    @Column(name = "auteur_action", length = 20)
    private String auteurAction;

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
