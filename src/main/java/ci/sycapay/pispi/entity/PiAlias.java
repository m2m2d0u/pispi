package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Minimal alias record per BCEAO PI-RAC v3.0.0 §4.1 — the participant is
 * forbidden from replicating the PI-RAC alias base locally. We only persist:
 * <ul>
 *   <li>the alias itself ({@code aliasValue}, {@code typeAlias},
 *       {@code codification})</li>
 *   <li>the participant code (always this PI)</li>
 *   <li>a <b>back-office client reference</b> ({@code backOfficeClientId}) —
 *       opaque FK to the participant's own client system. The nominal use is
 *       grouping a client's alias family (MBNO+SHID) so the cascade works
 *       without replicating the national ID.</li>
 *   <li>operational codes for the account ({@code numeroCompte},
 *       {@code typeCompte}) and discriminators used by the modification /
 *       duplicate paths ({@code typeClient}, {@code typeIdentifiant}).</li>
 *   <li>flow metadata (ids, statut, dates).</li>
 * </ul>
 *
 * <p><b>No PII is stored here.</b> Name, birth data, address, phone, email,
 * national/passport/RCCM identifier values and merchant descriptors are
 * forwarded to PI-RAC on creation/modification and then read back through
 * the participant's own back-office (by {@code backOfficeClientId}) or via a
 * fresh {@code RAC_SEARCH} for someone else's alias — never from here.
 */
@Entity
@Table(name = "pi_alias")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "end_to_end_id", nullable = false, length = 35)
    private String endToEndId;

    /** Nullable for SHID — PI-RAC generates the value in the creation callback. */
    @Column(name = "alias_value", length = 50)
    private String aliasValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_alias", nullable = false, length = 10)
    private TypeAlias typeAlias;

    /**
     * Opaque reference to the participant's own client record. Supplied by the
     * back office at creation time and used to correlate aliases owned by the
     * same client (MBNO + SHID cascade, modification fan-out, etc.). MUST NOT
     * contain any PII — pick a surrogate identifier.
     */
    @Column(name = "back_office_client_id", length = 64)
    private String backOfficeClientId;

    /**
     * Participant-type discriminator (P/B/G/C). Retained as an operational
     * code used to decide MCOD eligibility, MBNO eligibility, etc. A code,
     * not personal data.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_client", columnDefinition = "varchar(1)")
    private TypeClient typeClient;

    /**
     * Identification system used when the alias was registered (NIDN/CCPT/TXID).
     * Needed by {@code modifyAlias} to decide whether a passport field update
     * is applicable. Code only — the identifier VALUE lives in PI-RAC and the
     * back-office system, never here.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_identifiant", length = 10)
    private CodeSystemeIdentification typeIdentifiant;

    @Column(name = "numero_compte", length = 34)
    private String numeroCompte;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte", length = 10)
    private TypeCompte typeCompte;

    @Column(name = "codification", length = 30, unique = true)
    private String codification;

    @Column(name = "code_membre_participant", length = 6)
    private String codeMembreParticipant;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private AliasStatus statut;

    @Column(name = "date_creation_rac")
    private LocalDateTime dateCreationRac;

    @Column(name = "date_modification_rac")
    private LocalDateTime dateModificationRac;

    @Column(name = "date_suppression_rac")
    private LocalDateTime dateSuppressionRac;

    /**
     * Timestamp of the last successful sync between the participant's client
     * data and PI-RAC. See {@code AliasSyncService} and BCEAO §4.4.
     */
    @Column(name = "date_last_sync")
    private LocalDateTime dateLastSync;

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
