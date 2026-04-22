package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_identity_verification")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiIdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", nullable = false, length = 35)
    private String msgId;

    @Column(name = "end_to_end_id", unique = true, nullable = false, length = 35)
    private String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    // -----------------------------------------------------------------------
    // Flow metadata (internal — PI-SPI tracks who initiated vs. who verified)
    // -----------------------------------------------------------------------

    /** Participant qui a initié la vérification. */
    @Column(name = "code_membre_payeur", length = 6)
    private String codeMembrePayeur;

    /** Participant qui a répondu à la vérification (= détient le compte). */
    @Column(name = "code_membre_paye", length = 6)
    private String codeMembrePaye;

    // -----------------------------------------------------------------------
    // BCEAO Identite — champs demandés en ACMT.023
    // -----------------------------------------------------------------------

    /** Code du participant détenteur du compte à vérifier (BCEAO {@code codeMembreParticipant}). */
    @Column(name = "code_membre_participant", length = 6)
    private String codeMembreParticipant;

    @Column(name = "iban_client", length = 34)
    private String ibanClient;

    @Column(name = "other_client", length = 70)
    private String otherClient;

    // -----------------------------------------------------------------------
    // BCEAO IdentiteReponse — champs renvoyés en ACMT.024
    // -----------------------------------------------------------------------

    @Column(name = "resultat_verification")
    private Boolean resultatVerification;

    @Column(name = "code_raison", length = 10)
    private String codeRaison;

    @Column(name = "msg_id_reponse", length = 35)
    private String msgIdReponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte", length = 4)
    private TypeCompte typeCompte;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client", columnDefinition = "varchar(1)")
    private TypeClient typeClient;

    @Column(name = "nom_client", length = 140)
    private String nomClient;

    @Column(name = "ville_client", length = 140)
    private String villeClient;

    @Column(name = "adresse_complete", length = 350)
    private String adresseComplete;

    @Column(name = "numero_identification", length = 35)
    private String numeroIdentification;

    @Enumerated(EnumType.STRING)
    @Column(name = "systeme_identification", length = 4)
    private CodeSystemeIdentification systemeIdentification;

    @Column(name = "numero_rccm_client", length = 35)
    private String numeroRCCMClient;

    @Column(name = "identification_fiscale_commercant", length = 35)
    private String identificationFiscaleCommercant;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "ville_naissance", length = 140)
    private String villeNaissance;

    @Column(name = "pays_naissance", length = 2)
    private String paysNaissance;

    @Column(name = "pays_residence", length = 2)
    private String paysResidence;

    @Column(name = "devise", length = 3)
    private String devise;

    // -----------------------------------------------------------------------
    // Legacy columns kept for backward compatibility (nullable — pre-V18 data)
    // -----------------------------------------------------------------------

    @Column(name = "numero_compte_paye", length = 34)
    private String numeroComptePaye;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte_paye", length = 10)
    private TypeCompte typeComptePaye;

    @Column(name = "nom_client_paye", length = 140)
    private String nomClientPaye;

    @Column(name = "prenom_client_paye", length = 140)
    private String prenomClientPaye;

    // -----------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private VerificationStatus statut;

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
