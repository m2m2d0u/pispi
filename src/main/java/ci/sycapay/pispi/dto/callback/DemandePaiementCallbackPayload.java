package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Inbound Request-to-Pay payload (PAIN.013 — schéma BCEAO {@code DemandePaiement}).
 * Poussé par l'AIP quand un autre participant envoie une demande de paiement
 * à ce PI. Tous les champs client sont à plat — aucun objet imbriqué.
 */
@Data
@Schema(description = "Inbound PAIN.013 — BCEAO DemandePaiement schema (flat)")
public class DemandePaiementCallbackPayload {

    @Schema(description = "Identifiant unique du message.", example = "MCIE001XJWPAIN013000000000000001")
    private String msgId;

    @Schema(description = "Identifiant bout-en-bout.", example = "ECIE001XJWE2E0001420260422000001")
    private String endToEndId;

    @Schema(description = "Identifiant unique attribué par le payé à cette demande de paiement.",
            example = "RTP-INV-20260422-001")
    private String identifiantDemandePaiement;

    @Schema(description = "Identifiant du front-office du demandeur (côté payé).",
            example = "BOUTIQUE-KONE")
    private String clientDemandeur;

    @Schema(description = "Date/heure d'exécution programmée (ISO 8601, optionnel).",
            example = "2026-05-15T10:00:00Z")
    private String dateHeureExecution;

    @Schema(description = "Date/heure limite pour l'acceptation par le payeur (ISO 8601).",
            example = "2026-04-29T23:59:59Z")
    private String dateHeureLimiteAction;

    @Schema(description = "Référence du lot (optionnel).", example = "BULK-20260422-001")
    private String referenceBulk;

    @Schema(description = "Code canal BCEAO (401|500|520|521|631).", example = "500")
    private String canalCommunication;

    @Schema(description = "Montant demandé (chaîne — pattern \\d+).", example = "50000")
    private String montant;

    @Schema(description = "Autorisation de modification du montant par le payeur (chaîne true|false).",
            example = "false")
    private String autorisationModificationMontant;

    @Schema(description = "Montant de remise si paiement immédiat (chaîne — pattern \\d+).",
            example = "5000")
    private String montantRemisePaiementImmediat;

    @Schema(description = "Taux de remise si paiement immédiat (chaîne — pattern \\d+).", example = "10")
    private String tauxRemisePaiementImmediat;

    @Schema(description = "Identifiant du mandat de prélèvement (optionnel).",
            example = "MAND-TONTINE-ESPOIR-042")
    private String identifiantMandat;

    @Schema(description = "Signature numérique du mandat.")
    private String signatureNumeriqueMandat;

    // -----------------------------------------------------------------------
    // Payeur (ce PI — le débiteur)
    // -----------------------------------------------------------------------

    @Schema(description = "Nom ou dénomination sociale du payeur.", example = "KOUASSI")
    private String nomClientPayeur;

    @Schema(description = "Type de client payeur (P|B|G|C).", example = "P")
    private String typeClientPayeur;

    @Schema(description = "Ville de résidence du payeur.", example = "Abidjan")
    private String villeClientPayeur;

    @Schema(description = "Adresse complète du payeur.", example = "Rue des Jardins, Cocody")
    private String adresseClientPayeur;

    @Schema(description = "Numéro d'identification du payeur.", example = "CI-NIDN-1988-0003456")
    private String numeroIdentificationClientPayeur;

    @Schema(description = "Système d'identification du payeur (TXID|CCPT|NIDN).", example = "NIDN")
    private String systemeIdentificationClientPayeur;

    @Schema(description = "Date de naissance du payeur (YYYY-MM-DD).", example = "1988-11-05")
    private String dateNaissanceClientPayeur;

    @Schema(description = "Ville de naissance du payeur.", example = "Bouaké")
    private String villeNaissanceClientPayeur;

    @Schema(description = "Pays de naissance du payeur (ISO alpha-2).", example = "CI")
    private String paysNaissanceClientPayeur;

    @Schema(description = "Pays de résidence du payeur (code UEMOA).", example = "CI")
    private String paysClientPayeur;

    @Schema(description = "Numéro RCCM du payeur (entreprises).", example = "CI-ABJ-2020-B-12345")
    private String numeroRCCMClientPayeur;

    @Schema(description = "IBAN du compte payeur.", example = "CI05CI12345678901234567890123")
    private String ibanClientPayeur;

    @Schema(description = "Autre identifiant du compte payeur (ex. téléphone pour EME).",
            example = "+2250707070707")
    private String otherClientPayeur;

    @Schema(description = "Type de compte payeur (CACC|SVGS|TRAN|LLSV|VACC|TRAL|TAXE).",
            example = "TRAN")
    private String typeCompteClientPayeur;

    @Schema(description = "Devise du compte payeur (toujours XOF).", example = "XOF")
    private String deviseCompteClientPayeur;

    @Schema(description = "Alias du compte payeur (obligatoire).", example = "+2250707070707")
    private String aliasClientPayeur;

    @Schema(description = "Code membre du participant du payeur.", example = "SNE001")
    private String codeMembreParticipantPayeur;

    @Schema(description = "Identification fiscale commerçant (payeur).",
            example = "CI-2019-FISC-987654")
    private String identificationFiscaleCommercantPayeur;

    // -----------------------------------------------------------------------
    // Payé (l'autre participant — le créancier qui demande)
    // -----------------------------------------------------------------------

    @Schema(description = "Nom ou dénomination sociale du payé.", example = "BOUTIQUE KONE")
    private String nomClientPaye;

    @Schema(description = "Type de client payé (P|B|G|C).", example = "B")
    private String typeClientPaye;

    @Schema(description = "Ville du payé (obligatoire).", example = "Abidjan")
    private String villeClientPaye;

    @Schema(description = "Latitude GPS du payé.", example = "5.3600")
    private String latitudeClientPaye;

    @Schema(description = "Longitude GPS du payé.", example = "-4.0083")
    private String longitudeClientPaye;

    @Schema(description = "Adresse du payé.", example = "Marché de Treichville, Stand 217")
    private String adresseClientPaye;

    @Schema(description = "Numéro d'identification du payé (obligatoire).",
            example = "CI-2020-B-5556677")
    private String numeroIdentificationClientPaye;

    @Schema(description = "Système d'identification du payé (obligatoire ; TXID|CCPT|NIDN).",
            example = "TXID")
    private String systemeIdentificationClientPaye;

    @Schema(description = "Numéro RCCM du payé.")
    private String numeroRCCMClientPaye;

    @Schema(description = "Date de naissance du payé (YYYY-MM-DD).")
    private String dateNaissanceClientPaye;

    @Schema(description = "Ville de naissance du payé.")
    private String villeNaissanceClientPaye;

    @Schema(description = "Pays de naissance du payé (ISO alpha-2).")
    private String paysNaissanceClientPaye;

    @Schema(description = "Pays de résidence du payé (code UEMOA).", example = "CI")
    private String paysClientPaye;

    @Schema(description = "IBAN du compte payé.", example = "CI08CI98765432109876543210987")
    private String ibanClientPaye;

    @Schema(description = "Autre identifiant du compte payé.", example = "+2252521987654")
    private String otherClientPaye;

    @Schema(description = "Type de compte payé (CACC|SVGS|TRAN|LLSV|VACC|TAXE — sans TRAL).",
            example = "CACC")
    private String typeCompteClientPaye;

    @Schema(description = "Devise du compte payé (toujours XOF).", example = "XOF")
    private String deviseCompteClientPaye;

    @Schema(description = "Alias du compte payé (obligatoire).", example = "BK-4421")
    private String aliasClientPaye;

    @Schema(description = "Identification fiscale commerçant (payé).")
    private String identificationFiscaleCommercantPaye;

    // -----------------------------------------------------------------------
    // Motif & document de référence
    // -----------------------------------------------------------------------

    @Schema(description = "Motif libre du paiement (max 140 car.).",
            example = "Règlement facture fournisseur")
    private String motif;

    @Schema(description = "Type de document de référence (CINV|CMCN|DISP|PUOR).", example = "CINV")
    private String typeDocumentReference;

    @Schema(description = "Numéro du document de référence.", example = "FACT-BK-2026-0108")
    private String numeroDocumentReference;

    @Schema(description = "Montant de l'achat (chaîne — pattern \\d+).", example = "50000")
    private String montantAchat;

    @Schema(description = "Montant à retirer en cash (chaîne — pattern \\d+).", example = "100000")
    private String montantRetrait;

    @Schema(description = "Frais de retrait (chaîne — pattern \\d+).", example = "500")
    private String fraisRetrait;
}
