package ci.sycapay.pispi.dto.transaction;

import ci.sycapay.pispi.enums.TransactionAction;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Base DTO for {@code POST /api/v1/transferts}, aligned on the BCEAO remote spec
 * ({@code TransactionInitiationRequete}). Jackson selects the concrete subtype
 * from the {@code action} discriminator:
 *
 * <pre>
 *   send_now      → {@link TransactionImmediatRequest}      (PACS.008)
 *   receive_now   → {@link TransactionDemandePaiementRequest} (PAIN.013)
 * </pre>
 *
 * <p>Note: {@code send_schedule} maps to two subclasses; Jackson resolves via
 * {@code @JsonSubTypes.Type(name = "send_schedule", value = TransactionProgrammeRequest.class)}
 * should send {@code action=send_schedule} and include {@code frequence} if they
 * mean a subscription — we default to Programme and let the service promote to
 * Abonnement when {@code frequence} is present. To keep things unambiguous at
 * the DTO layer we expose a single {@code send_schedule} subtype with an optional
 * {@code frequence} field.
 *
 * <p>Common fields live on this class (see spec's {@code TransactionInitiationDatas}).
 */
@Data
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "action",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionImmediatRequest.class, name = "send_now"),
        @JsonSubTypes.Type(value = TransactionDemandePaiementRequest.class, name = "receive_now"),
        @JsonSubTypes.Type(value = TransactionScheduleRequest.class, name = "send_schedule")
})
public abstract class TransactionInitiationRequest {

    /** Type d'opération (discriminator). */
    @NotNull
    private TransactionAction action;

    /** Numéro de compte du client connecté (le payeur pour send_*, le payé pour receive_now). */
    @NotBlank
    private String compte;

    /** Montant de la transaction (minimum 1 per spec). */
    @NotNull
    @DecimalMin("1.0")
    private BigDecimal montant;

    /** Canal — strings per spec: {@code 633, 731, 400, 631, 500, 520, 521, 401, 000, 999}. */
    @NotBlank
    private String canal;

    /** GPS latitude du payeur. */
    @NotBlank
    private String latitude;

    /** GPS longitude du payeur. */
    @NotBlank
    private String longitude;

    /** Motif libre du transfert. */
    private String motif;

    /**
     * endToEndId d'une recherche d'alias (RAC_SEARCH) inbound pour le payeur
     * (le client connecté).
     *
     * <p><b>Note transitoire.</b> La spec mobile BCEAO suppose que le backend
     * connaît l'identité du client connecté via le contexte OAuth. En attendant
     * que ce contexte soit câblé, nous demandons au client mobile de fournir
     * l'{@code endToEndId} de la RAC_SEARCH qu'il a déjà déclenchée sur son
     * propre alias (les applications mobiles BCEAO le font systématiquement au
     * démarrage pour construire leur profil). Il sera retiré lorsque
     * l'identité du client sera résolue depuis Spring Security.
     */
    @jakarta.validation.constraints.NotBlank
    private String endToEndIdSearchPayeur;
}
