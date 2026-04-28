package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "pi_transfer",
        // Composite unique on (end_to_end_id, direction) — see V42 migration.
        // Allows OUTBOUND/INBOUND coexistence (self-loop sandbox + multi-tenant
        // entre deux participants gérés par cette plateforme).
        uniqueConstraints = @UniqueConstraint(
                name = "idx_transfer_e2e",
                columnNames = {"end_to_end_id", "direction"}
        )
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@ToString
public class PiTransfer {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "type_transaction", length = 10)
    private TypeTransaction typeTransaction;

    @Column(name = "canal_communication", length = 10)
    private CanalCommunication canalCommunication;

    @Column(name = "montant", precision = 18, scale = 2)
    private BigDecimal montant;

    @Column(name = "devise", length = 3)
    private String devise;

    @Column(name = "code_membre_payeur", length = 6)
    private String codeMembrePayeur;

    @Column(name = "numero_compte_payeur", length = 34)
    private String numeroComptePayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_payeur", length = 10)
    private TypeCompte typeComptePayeur;

    @Column(name = "nom_client_payeur", length = 140)
    private String nomClientPayeur;

    @Column(name = "prenom_client_payeur", length = 140)
    private String prenomClientPayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client_payeur", length = 5)
    private TypeClient typeClientPayeur;

    @Column(name = "telephone_payeur", length = 20)
    private String telephonePayeur;

    @Column(name = "code_membre_paye", length = 6)
    private String codeMembrePaye;

    @Column(name = "numero_compte_paye", length = 34)
    private String numeroComptePaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_paye", length = 10)
    private TypeCompte typeComptePaye;

    @Column(name = "nom_client_paye", length = 140)
    private String nomClientPaye;

    @Column(name = "prenom_client_paye", length = 140)
    private String prenomClientPaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client_paye", length = 5)
    private TypeClient typeClientPaye;

    @Column(name = "telephone_paye", length = 20)
    private String telephonePaye;

    @Column(name = "motif", length = 140)
    private String motif;

    /**
     * Identifiant de transaction ({@code <CdtTrfTxInf>/<PmtId>/<TxId>}).
     * Required by the BCEAO AIP for canals 400/733/500/521/520/631/401
     * (spec §4.3.1.1, V27 migration).
     */
    @Column(name = "identifiant_transaction", length = 35)
    private String identifiantTransaction;

    /**
     * Lien explicite vers le PAIN.013 (PiRequestToPay) que ce PACS.008 finalise
     * (V44). Renseigné uniquement quand le transfer est issu d'une acceptation
     * RTP via {@code confirmRtpAcceptance}. Permet au callback PACS.002 de
     * remonter sans ambiguïté à la ligne RTP correspondante (avant V44 : via
     * {@code findFirstByEndToEndIdOrderByIdDesc} + filtre PREVALIDATION,
     * fragile en multi-tenant).
     */
    @Column(name = "rtp_end_to_end_id", length = 35)
    private String rtpEndToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private TransferStatus statut;

    @Column(name = "code_raison", length = 50)
    private String codeRaison;

    @Column(name = "detail_echec", length = 500)
    private String detailEchec;

    @Column(name = "msg_id_reponse", length = 35)
    private String msgIdReponse;

    @Column(name = "date_heure_execution", nullable = false)
    private LocalDateTime dateHeureExecution;

    @Column(name = "date_heure_irrevocabilite")
    private LocalDateTime dateHeureIrrevocabilite;

    // -------------------------------------------------------------------------
    // V24 — mobile two-phase flow snapshot columns
    // -------------------------------------------------------------------------
    // Payé identity snapshot (built from a RAC_SEARCH result at init time)
    @Column(name = "pays_client_paye", length = 2)
    private String paysClientPaye;

    @Column(name = "identifiant_client_paye", length = 35)
    private String identifiantClientPaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_identifiant_client_paye", length = 4)
    private CodeSystemeIdentification typeIdentifiantClientPaye;

    @Column(name = "alias_paye", length = 50)
    private String aliasPaye;

    @Column(name = "iban_client_paye", length = 34)
    private String ibanClientPaye;

    @Column(name = "identification_fiscale_commercant_paye", length = 35)
    private String identificationFiscaleCommercantPaye;

    @Column(name = "identification_rccm_client_paye", length = 35)
    private String identificationRccmClientPaye;

    @Column(name = "ville_client_paye", length = 35)
    private String villeClientPaye;

    @Column(name = "adresse_client_paye", length = 140)
    private String adresseClientPaye;

    // Payeur identity snapshot
    @Column(name = "pays_client_payeur", length = 2)
    private String paysClientPayeur;

    @Column(name = "identifiant_client_payeur", length = 35)
    private String identifiantClientPayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_identifiant_client_payeur", length = 4)
    private CodeSystemeIdentification typeIdentifiantClientPayeur;

    @Column(name = "alias_payeur", length = 50)
    private String aliasPayeur;

    @Column(name = "iban_client_payeur", length = 34)
    private String ibanClientPayeur;

    @Column(name = "identification_fiscale_commercant_payeur", length = 35)
    private String identificationFiscaleCommercantPayeur;

    @Column(name = "identification_rccm_client_payeur", length = 35)
    private String identificationRccmClientPayeur;

    @Column(name = "ville_client_payeur", length = 35)
    private String villeClientPayeur;

    @Column(name = "adresse_client_payeur", length = 140)
    private String adresseClientPayeur;

    // Confirmation metadata (PUT /transferts/{id})
    @Column(name = "confirmation_date")
    private LocalDateTime confirmationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_methode", length = 10)
    private ConfirmationMethode confirmationMethode;

    @Column(name = "latitude_client_payeur", length = 20)
    private String latitudeClientPayeur;

    @Column(name = "longitude_client_payeur", length = 20)
    private String longitudeClientPayeur;

    // Audit — link back to the RAC_SEARCH log entry that produced each snapshot
    @Column(name = "rac_search_ref_paye", length = 50)
    private String racSearchRefPaye;

    @Column(name = "rac_search_ref_payeur", length = 50)
    private String racSearchRefPayeur;

    // -------------------------------------------------------------------------
    // V25 — schedule (Programme / Abonnement) metadata
    // -------------------------------------------------------------------------
    // On a schedule parent row: action=SEND_SCHEDULE carries the recipe.
    // On an immediate transfer:  action=SEND_NOW (or null for legacy rows).
    // On a spawned execution:    action=SEND_NOW + parent_schedule_id set.
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 15)
    private TransactionAction action;

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    // Hibernate 7 maps @Enumerated(STRING) + length=1 to CHAR(1) by default;
    // pin to varchar(1) to match the V25 migration (same workaround we use
    // for type_client elsewhere in the entity graph).
    @Enumerated(EnumType.STRING)
    @Column(name = "frequence", length = 1, columnDefinition = "varchar(1)")
    private TransactionFrequence frequence;

    @Column(name = "periodicite")
    private Integer periodicite;

    @Column(name = "next_execution_date")
    private java.time.LocalDate nextExecutionDate;

    @Column(name = "parent_schedule_id")
    private Long parentScheduleId;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "subscription_id", length = 50)
    private String subscriptionId;

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
