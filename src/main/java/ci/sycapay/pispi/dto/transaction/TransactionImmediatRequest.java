package ci.sycapay.pispi.dto.transaction;

import ci.sycapay.pispi.enums.CodeTypeDocument;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * {@code action = send_now}. Immediate debit — emits a PACS.008 after confirmation.
 *
 * <p>BCEAO §4.3 : tout transfert est précédé soit d'une recherche d'alias
 * (RAC_SEARCH), soit d'une vérification d'identité (ACMT.023/024). Le caller
 * choisit exactement un mode :
 *
 * <ul>
 *   <li><b>{@code alias}</b> — alias BCEAO (MBNO / SHID / MCOD). PI-SPI résout
 *       le bénéficiaire depuis le dernier RAC_SEARCH journalisé pour cet alias.
 *       L'{@code endToEndId} du PACS.008 réutilise celui du RAC_SEARCH.</li>
 *   <li><b>{@code endToEndIdVerification}</b> — {@code endToEndId} d'une
 *       ACMT.024 INBOUND déjà journalisée (résultat d'une vérification
 *       d'identité initiée via {@code POST /api/v1/verifications}). PI-SPI
 *       résout l'identité du bénéficiaire depuis le payload ACMT.024 et
 *       réutilise cet e2e pour le PACS.008 (chaînage ACMT.023 ↔ PACS.008
 *       imposé par la spec).</li>
 * </ul>
 *
 * <p>QR code flows (canal {@code 731}) carry {@code alias} plus an optional
 * {@code txId} for dynamic QRs.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionImmediatRequest extends TransactionInitiationRequest {

    /**
     * Alias (MBNO/SHID/MCOD) du bénéficiaire. Mutuellement exclusif avec
     * {@code endToEndIdVerification}. PI-SPI résout l'identité depuis la
     * dernière RAC_SEARCH journalisée pour cet alias.
     */
    private String alias;

    /**
     * {@code endToEndId} d'une ACMT.024 INBOUND déjà journalisée (résultat
     * d'une vérification d'identité). Mutuellement exclusif avec {@code alias}.
     * PI-SPI résout l'identité du bénéficiaire (compte, type, naissance,
     * identification) depuis le payload de cette vérification, et réutilise
     * cet e2e pour le PACS.008.
     *
     * <p>Pré-requis : avoir effectué une vérification via
     * {@code POST /api/v1/verifications} et reçu la réponse ACMT.024 (statut
     * VERIFIED).
     */
    private String endToEndIdVerification;

    /**
     * Identifiant de transaction — correspond à {@code identifiantTransaction}
     * dans le payload PACS.008 ({@code <CdtTrfTxInf>/<PmtId>/<TxId>}).
     *
     * <p>Utilisé pour plusieurs canaux :
     * <ul>
     *   <li>{@code 731} (QR dynamique) — identifiant extrait du QR</li>
     *   <li>{@code 400, 733, 500, 521, 520, 631, 401} — identifiant requis
     *       par l'AIP pour ces canaux. Fourni par l'app mobile (POS,
     *       e-commerce, facture) ou, si absent, auto-généré par le backend
     *       au moment de l'initiation.</li>
     * </ul>
     */
    private String txId;

    /**
     * Référence utilisée pour identifier les transactions d'un virement de masse.
     * Optionnel — maps to {@code <CdtTrfTxInf>/<PmtId>/<InstrId>}, max 35 chars.
     */
    @Size(max = 35)
    private String referenceBulk;

    /**
     * Montant de l'achat (optionnel — canaux marchands 500/521/520/400/733).
     * Maps to {@code <RmtInf>/<Strd>/<LineDtls>/<Amt>/<DuePyblAmt>}.
     */
    private BigDecimal montantAchat;

    /**
     * Montant du retrait cashback (optionnel — canaux marchands).
     * Maps to {@code <RmtInf>/<Strd>/<LineDtls>/<Amt>/<DuePyblAmt>}.
     */
    private BigDecimal montantRetrait;

    /**
     * Frais de retrait supportés par le client (optionnel).
     * Maps to {@code <RmtInf>/<Strd>/<LineDtls>/<Amt>/<AdjstmntAmtAndRsn>/<Amt>}.
     */
    private BigDecimal fraisRetrait;

    /**
     * Type du document justificatif (facture, document commercial, etc.).
     * Optionnel — maps to {@code <RmtInf>/<Strd>/<RfrdDocInf>/<Tp>/<CdOrPrtry>/<Cd>}.
     */
    private CodeTypeDocument typeDocumentReference;

    /**
     * Numéro de référence du document lié au virement.
     * Optionnel — maps to {@code <RmtInf>/<Strd>/<RfrdDocInf>/<Nb>}.
     */
    @Size(max = 35)
    private String numeroDocumentReference;

    /**
     * Exactement un mode d'identification du bénéficiaire est requis :
     * soit {@code alias} (RAC_SEARCH), soit {@code endToEndIdVerification}
     * (ACMT.024). On valide en DTO pour échouer en 400 propre avant d'atteindre
     * le résolver.
     */
    @JsonIgnore
    @AssertTrue(message = "Un et un seul mode d'identification du bénéficiaire est requis : "
            + "'alias' (transfert via RAC_SEARCH) OU 'endToEndIdVerification' "
            + "(transfert via vérification d'identité ACMT.023/024)")
    public boolean isBeneficiaryIdentifierValid() {
        boolean hasAlias = alias != null && !alias.isBlank();
        boolean hasVerif = endToEndIdVerification != null && !endToEndIdVerification.isBlank();
        return (hasAlias ^ hasVerif);   // XOR — exactement un des deux
    }
}
