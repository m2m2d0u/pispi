package ci.sycapay.pispi.dto.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * {@code action = send_now}. Immediate debit — emits a PACS.008 after confirmation.
 *
 * <p>The BCEAO spec defines three beneficiary identification modes, which the
 * client picks exactly one of:
 * <ul>
 *   <li>{@code alias} — alias (MBNO/SHID/MCOD). PSP is looked up via RAC_SEARCH.</li>
 *   <li>{@code iban} + {@code payePSP} — bank account. No RAC_SEARCH needed but
 *       we still emit one for consistency (per §4.2, no caching is allowed).</li>
 *   <li>{@code othr} + {@code payePSP} — non-IBAN (EME/TRAL) account.</li>
 * </ul>
 *
 * <p>QR code flows (canal {@code 731}) carry {@code alias} plus an optional
 * {@code txId} for dynamic QRs.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionImmediatRequest extends TransactionInitiationRequest {

    /** Alias (MBNO/SHID/MCOD) du bénéficiaire — exclusif avec iban/othr. */
    private String alias;

    /** IBAN du bénéficiaire — doit être accompagné de payePSP. */
    private String iban;

    /** Autre référence de compte (EME/TRAL) — doit être accompagnée de payePSP. */
    private String othr;

    /** Code membre du PSP du bénéficiaire (requis avec iban ou othr). */
    private String payePSP;

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
     * Exactly one of the three beneficiary-identification modes must be provided.
     * Keep this validation in the DTO so a malformed request fails fast with a
     * clear 400 before we touch the resolver.
     */
    @JsonIgnore
    @AssertTrue(message = "Un et un seul mode d'identification du bénéficiaire est requis : "
            + "'alias' OU ('iban' + 'payePSP') OU ('othr' + 'payePSP')")
    public boolean isBeneficiaryIdentifierValid() {
        boolean hasAlias = alias != null && !alias.isBlank();
        boolean hasIban = iban != null && !iban.isBlank();
        boolean hasOthr = othr != null && !othr.isBlank();
        boolean hasPsp = payePSP != null && !payePSP.isBlank();

        int modes = (hasAlias ? 1 : 0) + (hasIban ? 1 : 0) + (hasOthr ? 1 : 0);
        if (modes != 1) return false;

        // iban/othr require payePSP; alias does not (PSP is resolved via RAC_SEARCH)
        if ((hasIban || hasOthr) && !hasPsp) return false;
        return true;
    }
}
