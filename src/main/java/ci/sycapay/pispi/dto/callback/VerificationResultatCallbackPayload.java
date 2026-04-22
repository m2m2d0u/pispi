package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Identity verification result payload (ACMT.024 — schéma BCEAO {@code IdentiteReponse}).
 * Poussé par l'AIP pour livrer le résultat d'une vérification initiée par ce PI.
 */
@Data
@Schema(description = "Inbound ACMT.024 — BCEAO IdentiteReponse schema")
public class VerificationResultatCallbackPayload {

    @Schema(description = "Identifiant unique de ce message de réponse.",
            example = "MCIE002XJWRESVER00000000000000001")
    private String msgId;

    @Schema(description = "Identifiant du message ACMT.023 d'origine (format AIP).",
            example = "MPIUMOAW8Os2wIHBS2j1XngvTOc0b6Ixffm")
    private String msgIdDemande;

    @Schema(description = "Identifiant bout-en-bout (repris de la demande).",
            example = "ECIE001XJWE2E00001VERIF000000001")
    private String endToEndId;

    @Schema(description = "Code du participant détenteur du compte vérifié.", example = "CIE002")
    private String codeMembreParticipant;

    /**
     * Le schéma BCEAO sérialise {@code resultatVerification} en chaîne ("true"|"false").
     */
    @Schema(description = "Résultat de la vérification (chaîne true|false).",
            example = "true", allowableValues = {"true", "false"})
    private String resultatVerification;

    @Schema(description = "Code ISO de rejet si resultatVerification=false (pattern BCEAO: AC01).",
            example = "AC01")
    private String codeRaison;

    // -----------------------------------------------------------------------
    // Champs renvoyés lorsque la vérification est positive
    // -----------------------------------------------------------------------

    @Schema(description = "IBAN du compte vérifié.", example = "CI05CI12345678901234567890123")
    private String ibanClient;

    @Schema(description = "Autre identifiant du compte vérifié.", example = "+2250707077777")
    private String otherClient;

    @Schema(description = "Type de compte (CACC|SVGS|TRAN|TRAL|TAXE).", example = "CACC")
    private String typeCompte;

    @Schema(description = "Type de client (P|B|G|C).", example = "P")
    private String typeClient;

    @Schema(description = "Nom du client (dénomination sociale pour B/G).", example = "Diallo")
    private String nomClient;

    @Schema(description = "Ville de résidence du client.", example = "Abidjan")
    private String villeClient;

    @Schema(description = "Adresse complète du client.",
            example = "12 Rue des Jardins, Cocody")
    private String adresseComplete;

    @Schema(description = "Numéro d'identification associé.", example = "CI-NIDN-1988-0003456")
    private String numeroIdentification;

    @Schema(description = "Système d'identification (TXID|CCPT|NIDN).", example = "NIDN")
    private String systemeIdentification;

    @Schema(description = "Numéro RCCM (entreprises).", example = "CI-ABJ-2020-B-12345")
    private String numeroRCCMClient;

    @Schema(description = "Identifiant fiscal du commerçant.", example = "CI-2019-FISC-987654")
    private String identificationFiscaleCommercant;

    @Schema(description = "Date de naissance (YYYY-MM-DD).", example = "1988-11-05")
    private String dateNaissance;

    @Schema(description = "Ville de naissance.", example = "Abidjan")
    private String villeNaissance;

    @Schema(description = "Pays de naissance (ISO 3166 alpha-2).", example = "CI")
    private String paysNaissance;

    @Schema(description = "Pays de résidence (code UEMOA).", example = "CI")
    private String paysResidence;

    @Schema(description = "Devise du compte (toujours XOF en zone UEMOA).", example = "XOF")
    private String devise;
}
