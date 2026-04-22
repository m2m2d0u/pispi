package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for responding to an inbound identity verification request
 * (ACMT.024 — schéma BCEAO {@code IdentiteReponse}).
 *
 * <p>When {@code resultatVerification} is {@code true}, the participant SHOULD
 * return the full client identity (name, account info, identification, etc.).
 * When {@code false}, {@code codeRaison} is required (BCEAO pattern: {@code AC01}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRespondRequest {

    @NotNull
    private Boolean resultatVerification;

    /** Code ISO de rejet — obligatoire si {@code resultatVerification = false}. BCEAO pattern: {@code AC01}. */
    @Pattern(regexp = "^AC01$", message = "codeRaison must be AC01 per BCEAO IdentiteReponse schema")
    private String codeRaison;

    // -----------------------------------------------------------------------
    // Champs renvoyés lorsque la vérification est positive (resultat = true)
    // -----------------------------------------------------------------------

    /** IBAN du compte vérifié (pour les banques). Exclusif avec {@code otherClient}. */
    @Size(max = 34)
    @Pattern(regexp = "^(?:CI|SN|ML|GW|BF|NE|BJ|TG)\\d{2}(BJ|BF|CI|GW|ML|NE|SN|TG)\\d{22}$",
            message = "ibanClient must be a valid UEMOA IBAN")
    private String ibanClient;

    /** Autre identifiant du compte vérifié (téléphone pour EME). Exclusif avec {@code ibanClient}. */
    @Size(max = 70)
    private String otherClient;

    /** Type de compte — schéma BCEAO: CACC|SVGS|TRAN|TRAL|TAXE. */
    private TypeCompte typeCompte;

    /** Type de client: P (personne physique), B (business), G (government), C (commercial). */
    private TypeClient typeClient;

    @Size(max = 140)
    private String nomClient;

    @Size(max = 140)
    private String villeClient;

    @Size(max = 350)
    private String adresseComplete;

    @Size(max = 35)
    private String numeroIdentification;

    /** Système d'identification associé au numéroIdentification (TXID|CCPT|NIDN). */
    private CodeSystemeIdentification systemeIdentification;

    @Size(max = 35)
    private String numeroRCCMClient;

    @Size(max = 35)
    private String identificationFiscaleCommercant;

    /** Format ISO 8601 (YYYY-MM-DD). */
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
            message = "dateNaissance must be in format YYYY-MM-DD")
    private String dateNaissance;

    @Size(max = 140)
    private String villeNaissance;

    @Pattern(regexp = "^[A-Z]{2}$",
            message = "paysNaissance must be uppercase ISO 3166 alpha-2 code")
    private String paysNaissance;

    /** Pays de résidence (ISO 3166 alpha-2 UEMOA). */
    @Pattern(regexp = "^(BJ|BF|CI|GW|ML|NE|SN|TG)$",
            message = "paysResidence must be a UEMOA country code")
    private String paysResidence;

    /** Devise du compte — toujours {@code XOF} pour la zone UEMOA. */
    @Pattern(regexp = "^XOF$", message = "devise must be XOF")
    private String devise;

    // -----------------------------------------------------------------------
    // Cross-field validations
    // -----------------------------------------------------------------------

    @JsonIgnore
    @AssertTrue(message = "codeRaison is required when resultatVerification is false and must be absent when true")
    public boolean isCodeRaisonConsistentWithResult() {
        if (resultatVerification == null) return true;
        boolean hasCodeRaison = codeRaison != null && !codeRaison.isBlank();
        return resultatVerification ? !hasCodeRaison : hasCodeRaison;
    }

    @JsonIgnore
    @AssertTrue(message = "At most one of ibanClient / otherClient may be provided")
    public boolean isAccountIdentifierSingular() {
        boolean iban = ibanClient != null && !ibanClient.isBlank();
        boolean other = otherClient != null && !otherClient.isBlank();
        return !(iban && other);
    }
}
