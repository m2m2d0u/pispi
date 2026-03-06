package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.EtatParticipant;
import ci.sycapay.pispi.enums.TypeParticipant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pi_participant")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_membre", unique = true, nullable = false, length = 6)
    private String codeMembre;

    @Column(name = "nom", length = 140)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat", length = 10)
    private EtatParticipant etat;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_participant", length = 5)
    private TypeParticipant typeParticipant;

    @Column(name = "pays", length = 2)
    private String pays;

    @Column(name = "participant_sponsor", length = 6)
    private String participantSponsor;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

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
