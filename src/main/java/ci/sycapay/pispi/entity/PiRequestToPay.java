package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_request_to_pay")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiRequestToPay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", nullable = false, length = 35)
    private String msgId;

    @Column(name = "end_to_end_id", unique = true, nullable = false, length = 35)
    private String endToEndId;

    @Column(name = "identifiant_demande_paiement", length = 35)
    private String identifiantDemandePaiement;

    @Column(name = "reference_bulk", length = 35)
    private String referenceBulk;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    /**
     * Stored as the 3-char BCEAO code ("401", "500", "520", "521", "631") via
     * {@link ci.sycapay.pispi.config.converter.CanalCommunicationRtpConverter}
     * (autoApply). Do NOT add {@code @Enumerated(EnumType.STRING)} — it would
     * override the converter and try to write the enum name, overflowing the
     * VARCHAR(3) column.
     */
    @Column(name = "canal_communication", length = 3)
    private CanalCommunicationRtp canalCommunication;

    @Column(name = "montant", precision = 18, scale = 2)
    private BigDecimal montant;

    @Column(name = "devise", length = 3)
    private String devise;

    @Column(name = "date_heure_execution")
    private LocalDateTime dateHeureExecution;

    @Column(name = "date_heure_limite_action")
    private LocalDateTime dateHeureLimiteAction;

    // -----------------------------------------------------------------------
    // Payeur party (BCEAO flat fields)
    // -----------------------------------------------------------------------

    @Column(name = "code_membre_payeur", length = 6)
    private String codeMembrePayeur;

    @Column(name = "alias_client_payeur", length = 140)
    private String aliasClientPayeur;

    @Column(name = "iban_client_payeur", length = 34)
    private String ibanClientPayeur;

    @Column(name = "other_client_payeur", length = 70)
    private String otherClientPayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_payeur", length = 10)
    private TypeCompte typeComptePayeur;

    @Column(name = "nom_client_payeur", length = 140)
    private String nomClientPayeur;

    @Column(name = "telephone_payeur", length = 20)
    private String telephonePayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client_payeur", columnDefinition = "varchar(1)")
    private TypeClient typeClientPayeur;

    @Column(name = "ville_client_payeur", length = 140)
    private String villeClientPayeur;

    @Column(name = "pays_payeur", length = 2)
    private String paysClientPayeur;

    @Column(name = "numero_identification_payeur", length = 35)
    private String numeroIdentificationPayeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "systeme_identification_payeur", length = 4)
    private CodeSystemeIdentification systemeIdentificationPayeur;

    @Column(name = "numero_rccm_payeur", length = 35)
    private String numeroRCCMPayeur;

    @Column(name = "date_naissance_payeur", length = 10)
    private String dateNaissancePayeur;

    @Column(name = "ville_naissance_payeur", length = 140)
    private String villeNaissancePayeur;

    @Column(name = "pays_naissance_payeur", length = 2)
    private String paysNaissancePayeur;

    @Column(name = "identification_fiscale_payeur", length = 35)
    private String identificationFiscalePayeur;

    // -----------------------------------------------------------------------
    // Paye party (BCEAO flat fields)
    // -----------------------------------------------------------------------

    @Column(name = "code_membre_paye", length = 6)
    private String codeMembrePaye;

    @Column(name = "alias_client_paye", length = 140)
    private String aliasClientPaye;

    @Column(name = "iban_client_paye", length = 34)
    private String ibanClientPaye;

    @Column(name = "other_client_paye", length = 70)
    private String otherClientPaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_paye", length = 10)
    private TypeCompte typeComptePaye;

    @Column(name = "nom_client_paye", length = 140)
    private String nomClientPaye;

    @Column(name = "telephone_paye", length = 20)
    private String telephonePaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client_paye", columnDefinition = "varchar(1)")
    private TypeClient typeClientPaye;

    @Column(name = "ville_client_paye", length = 140)
    private String villeClientPaye;

    @Column(name = "pays_paye", length = 2)
    private String paysClientPaye;

    @Column(name = "numero_identification_paye", length = 35)
    private String numeroIdentificationPaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "systeme_identification_paye", length = 4)
    private CodeSystemeIdentification systemeIdentificationPaye;

    @Column(name = "numero_rccm_paye", length = 35)
    private String numeroRCCMPaye;

    @Column(name = "date_naissance_paye", length = 10)
    private String dateNaissancePaye;

    @Column(name = "ville_naissance_paye", length = 140)
    private String villeNaissancePaye;

    @Column(name = "pays_naissance_paye", length = 2)
    private String paysNaissancePaye;

    @Column(name = "identification_fiscale_paye", length = 35)
    private String identificationFiscalePaye;

    @Column(name = "latitude_client_paye", length = 20)
    private String latitudeClientPaye;

    @Column(name = "longitude_client_paye", length = 20)
    private String longitudeClientPaye;

    // -----------------------------------------------------------------------
    // Transaction details
    // -----------------------------------------------------------------------

    @Column(name = "motif", length = 140)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document_reference", length = 4)
    private CodeTypeDocument typeDocumentReference;

    @Column(name = "numero_document_reference", length = 35)
    private String numeroDocumentReference;

    @Column(name = "autorisation_modification_montant")
    private Boolean autorisationModificationMontant;

    @Column(name = "montant_achat", precision = 18, scale = 2)
    private BigDecimal montantAchat;

    @Column(name = "montant_retrait", precision = 18, scale = 2)
    private BigDecimal montantRetrait;

    @Column(name = "frais_retrait", precision = 18, scale = 2)
    private BigDecimal fraisRetrait;

    @Column(name = "montant_remise_paiement_immediat", precision = 18, scale = 2)
    private BigDecimal montantRemisePaiementImmediat;

    /** Percentage rate remise (e.g. 5.0 = 5 %). Mutually exclusive with montantRemisePaiementImmediat. */
    @Column(name = "taux_remise_paiement_immediat", precision = 10, scale = 4)
    private BigDecimal tauxRemisePaiementImmediat;

    // -----------------------------------------------------------------------
    // Status tracking
    // -----------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private RtpStatus statut;

    @Column(name = "code_raison", length = 10)
    private String codeRaison;

    @Column(name = "msg_id_reponse", length = 35)
    private String msgIdReponse;

    @Column(name = "transfer_end_to_end_id", length = 35)
    private String transferEndToEndId;

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
