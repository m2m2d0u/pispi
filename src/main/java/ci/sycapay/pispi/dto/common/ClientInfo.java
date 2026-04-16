package ci.sycapay.pispi.dto.common;

import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.TypeClient;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client information for alias creation.
 *
 * Required fields per BCEAO OpenAPI spec:
 * - For type P (personne physique): nom, nationalite, paysResidence, telephone, categorie, genre,
 *   identificationNationale (or numeroPasseport), dateNaissance, paysNaissance, villeNaissance
 * - For type C (commercial): same as P
 * - For type B (business): nom, nationalite, paysResidence, telephone, categorie, raisonSociale
 * - For type G (government): nom, nationalite, paysResidence, telephone, categorie, raisonSociale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientInfo {

    /** Nom du client. Pour une entreprise, il s'agit de la dénomination sociale. */
    @NotBlank(message = "nom is required")
    @Size(max = 140, message = "nom must not exceed 140 characters")
    private String nom;

    /** Prénom (non supporté par interface-participant - ignoré lors de la transmission). */
    @Size(max = 140)
    private String prenom;

    /** Autre prénom (non supporté par interface-participant - ignoré lors de la transmission). */
    @Size(max = 140)
    private String autrePrenom;

    /** Raison sociale de l'entreprise. */
    @Size(max = 140, message = "raisonSociale must not exceed 140 characters")
    private String raisonSociale;

    /** Dénomination sociale. */
    @Size(max = 35, message = "denominationSociale must not exceed 35 characters")
    private String denominationSociale;

    /**
     * Genre du client: "1" = Masculin, "2" = Féminin.
     * Obligatoire pour les types P (personne physique) et C (commercial).
     */
    @Pattern(regexp = "^[12]$", message = "genre must be '1' (Masculin) or '2' (Féminin)")
    private String genre;

    /** Type de client: P (personne physique), B (business), G (government), C (commercial). */
    @NotNull(message = "typeClient is required")
    private TypeClient typeClient;

    /**
     * Type d'identifiant: NIDN (national ID), CCPT (passport), TXID (fiscal ID).
     * Required for all clients EXCEPT persons without identification (typeCompte: TRAL).
     */
    private CodeSystemeIdentification typeIdentifiant;

    /**
     * Numéro d'identification (nationale, passeport ou fiscale selon typeIdentifiant).
     * Required for all clients EXCEPT persons without identification (typeCompte: TRAL).
     */
    @Size(max = 35, message = "identifiant must not exceed 35 characters")
    private String identifiant;

    /**
     * Date de naissance au format ISO 8601 (YYYY-MM-DD).
     * Obligatoire pour les types P et C per BCEAO spec.
     */
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
             message = "dateNaissance must be in format YYYY-MM-DD")
    private String dateNaissance;

    /**
     * Ville de naissance.
     * Obligatoire pour les types P et C per BCEAO spec.
     */
    @Size(max = 35, message = "lieuNaissance must not exceed 35 characters")
    private String lieuNaissance;

    /**
     * Pays de naissance au format ISO 3166 alpha-2 (ex: CI, SN, BF).
     * Obligatoire pour les types P et C per BCEAO spec.
     */
    @Size(min = 2, max = 2, message = "paysNaissance must be ISO 3166 alpha-2 code (2 characters)")
    @Pattern(regexp = "^[A-Z]{2}$", message = "paysNaissance must be uppercase ISO 3166 alpha-2 code")
    private String paysNaissance;

    /**
     * Nationalité au format ISO 3166 alpha-2.
     * Obligatoire pour les types P et C.
     */
    @Size(min = 2, max = 2, message = "nationalite must be ISO 3166 alpha-2 code (2 characters)")
    @Pattern(regexp = "^[A-Z]{2}$", message = "nationalite must be uppercase ISO 3166 alpha-2 code")
    private String nationalite;

    /** Adresse du client. */
    @Size(max = 140, message = "adresse must not exceed 140 characters")
    private String adresse;

    /** Ville de résidence. */
    @Size(max = 140, message = "ville must not exceed 140 characters")
    private String ville;

    /**
     * Pays de résidence au format ISO 3166 alpha-2.
     * Obligatoire - doit être un pays UEMOA (CI, SN, ML, BF, NE, BJ, TG, GW).
     */
    @NotBlank(message = "pays is required")
    @Size(min = 2, max = 2, message = "pays must be ISO 3166 alpha-2 code (2 characters)")
    @Pattern(regexp = "^(CI|SN|ML|BF|NE|BJ|TG|GW)$",
             message = "pays must be a UEMOA country code: CI, SN, ML, BF, NE, BJ, TG, or GW")
    private String pays;

    /**
     * Numéro de téléphone au format E.164 UEMOA.
     * Format: +{code_pays}{8-12 chiffres}
     * Codes pays UEMOA: 223 (Mali), 226 (Burkina), 228 (Togo), 229 (Bénin),
     *                   225 (Côte d'Ivoire), 221 (Sénégal), 227 (Niger), 245 (Guinée-Bissau)
     */
    @NotBlank(message = "telephone is required")
    @Size(max = 20, message = "telephone must not exceed 20 characters")
    @Pattern(regexp = "^\\+(?:223|226|228|229|225|221|227|245)\\d{8,12}$",
             message = "telephone must be E.164 format with UEMOA country code (e.g., +2250707070707)")
    private String telephone;

    /** Adresse email du client. */
    @Email(message = "email must be a valid email address")
    @Size(max = 254, message = "email must not exceed 254 characters")
    private String email;

    /** Code postal. */
    @Size(max = 20, message = "codePostal must not exceed 20 characters")
    private String codePostal;

    /** Nom de la mère (optionnel). */
    @Size(max = 140, message = "nomMere must not exceed 140 characters")
    private String nomMere;

    // ---- Cross-field validations ----
    // NOTE: Most "required for P/C" validations are moved to AliasCreationRequest
    // because they depend on typeCompte (TRAL exempts these requirements).

    /**
     * Validates that raisonSociale is provided for type B (business) and G (government) clients.
     * B/G clients cannot use TRAL, so this validation always applies.
     */
    @JsonIgnore
    @AssertTrue(message = "raisonSociale is required for type B (business) and G (government) clients")
    public boolean isRaisonSocialeValidForClientType() {
        if (typeClient == TypeClient.B || typeClient == TypeClient.G) {
            return raisonSociale != null && !raisonSociale.isBlank();
        }
        return true;
    }

    /**
     * Validates identifier type matches client type when identification is provided.
     * - Type P/C: should use NIDN or CCPT
     * - Type B/G: should use TXID
     */
    @JsonIgnore
    @AssertTrue(message = "typeIdentifiant must match typeClient: P/C should use NIDN or CCPT, B/G should use TXID")
    public boolean isIdentifiantTypeValidForClientType() {
        if (typeClient == null || typeIdentifiant == null) {
            return true; // Identification is optional for TRAL accounts
        }
        return switch (typeClient) {
            case P, C -> typeIdentifiant == CodeSystemeIdentification.NIDN ||
                         typeIdentifiant == CodeSystemeIdentification.CCPT;
            case B, G -> typeIdentifiant == CodeSystemeIdentification.TXID;
        };
    }

    // NOTE: prenomClient is NOT supported by interface-participant DTO (BCEAO limitation).
    // The prenom field is kept for future compatibility but not validated or sent.
}
