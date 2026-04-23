package ci.sycapay.pispi.dto.rtp;

import ci.sycapay.pispi.enums.CanalCommunicationRtp;
import ci.sycapay.pispi.enums.CodeTypeDocument;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload for initiating an outbound Request-to-Pay (PAIN.013 — BCEAO
 * {@code DemandePaiement}).
 *
 * <p>Lean pattern: the payeur and payé are identified by the {@code endToEndId}
 * of a prior inbound RAC_SEARCH log entry. The service resolves the full
 * client identity (nom, type, iban/other, alias, codeMembreParticipant, etc.)
 * from {@code pi_message_log} and flattens it into the BCEAO payload.
 *
 * <p>Only the fields that the caller must supply on top of the resolved data
 * are carried here (canal, amount, motif, remise, document reference, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestToPayRequest {

    /** BCEAO canal codes allowed for RTP: 401|500|520|521|631. */
    @NotNull
    private CanalCommunicationRtp canalCommunication;

    /**
     * BCEAO pain.013 {@code InitgPty>Nm} — initiating party name.
     *
     * <p>AIP rule: must be either the literal {@code "X"} (the initiating
     * party is the payé client) or an {@code https://} URL pointing to a
     * logo/image. Any other value is rejected with
     * {@code "InitgPty>Nm doit contenir X si c'est le nom du client payé ou
     * une le lien vers une image commencant par https://"}.
     *
     * <p>Leave blank to let the service default to {@code "X"}.
     */
    @Size(max = 140)
    @Pattern(regexp = "^(X|https://.*)$",
            message = "clientDemandeur must be either 'X' or an https:// URL per BCEAO InitgPty rule")
    private String clientDemandeur;

    @NotBlank
    @Size(max = 35)
    private String identifiantDemandePaiement;

    @NotNull
    @Positive
    private BigDecimal montant;

    /** ISO 8601 execution time (deferred-debit scenarios). */
    private String dateHeureExecution;

    /** ISO 8601 deadline for the payer to accept/reject. */
    private String dateHeureLimiteAction;

    /**
     * endToEndId of an inbound RAC_SEARCH log entry resolving the payeur
     * (the debtor — handled by another participant).
     */
    @NotBlank
    @Size(max = 140)
    private String endToEndIdSearchPayeur;

    /**
     * endToEndId of an inbound RAC_SEARCH log entry resolving the payé
     * (the creditor — a client of this PI).
     */
    @NotBlank
    @Size(max = 140)
    private String endToEndIdSearchPaye;

    @Size(max = 35)
    private String identificationFiscaleCommercantPayeur;

    @Size(max = 35)
    private String identificationFiscaleCommercantPaye;

    /** GPS coordinates of the payé (optional — some merchant canals carry them). */
    @Size(max = 20)
    private String latitudeClientPaye;

    @Size(max = 20)
    private String longitudeClientPaye;

    @Size(max = 140)
    private String motif;

    @Size(max = 35)
    private String referenceBulk;

    private Boolean autorisationModificationMontant;

    /** Mutually exclusive with {@link #tauxRemisePaiementImmediat}. */
    @PositiveOrZero
    private BigDecimal montantRemisePaiementImmediat;

    /** Mutually exclusive with {@link #montantRemisePaiementImmediat}. */
    @PositiveOrZero
    private BigDecimal tauxRemisePaiementImmediat;

    @Size(max = 35)
    private String identifiantMandat;

    private String signatureNumeriqueMandat;

    /** BCEAO typeDocumentReference: CINV|CMCN|DISP|PUOR. */
    private CodeTypeDocument typeDocumentReference;

    @Size(max = 35)
    private String numeroDocumentReference;

    @PositiveOrZero
    private BigDecimal montantAchat;

    @PositiveOrZero
    private BigDecimal montantRetrait;

    @PositiveOrZero
    private BigDecimal fraisRetrait;

    // -----------------------------------------------------------------------
    // Cross-field validations
    // -----------------------------------------------------------------------

    @JsonIgnore
    @AssertTrue(message = "montantRemisePaiementImmediat and tauxRemisePaiementImmediat are mutually exclusive")
    public boolean isRemisePaiementImmediatSingular() {
        return !(montantRemisePaiementImmediat != null && tauxRemisePaiementImmediat != null);
    }
}
