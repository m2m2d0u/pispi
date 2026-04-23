package ci.sycapay.pispi.dto.transaction;

import ci.sycapay.pispi.enums.TransactionFrequence;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * {@code action = send_schedule}. Covers both the {@code TransactionProgramme}
 * (one-off deferred transfer) and {@code TransactionAbonnement} (recurring)
 * cases from the BCEAO remote spec. Jackson can't distinguish them by property
 * alone (both share {@code action=send_schedule, canal=633}), so we fold them
 * into a single DTO and let the service promote it to a subscription when
 * {@code frequence} is present.
 *
 * <ul>
 *   <li>{@code dateDebut} only → Programme (single deferred execution)</li>
 *   <li>{@code dateDebut} + {@code frequence} → Abonnement (recurring)</li>
 * </ul>
 *
 * <p>Like {@link TransactionImmediatRequest}, the beneficiary is identified by
 * exactly one of alias / iban+payePSP / othr+payePSP. Nothing is emitted to
 * the AIP at initiation time — the scheduler picks this up on the execution
 * date and runs the normal PACS.008 flow.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionScheduleRequest extends TransactionInitiationRequest {

    /** Date de première exécution (obligatoire). */
    @NotNull
    private OffsetDateTime dateDebut;

    /** Date de dernière exécution (abonnement uniquement). */
    private OffsetDateTime dateFin;

    /** Fréquence — requise pour un abonnement, absente pour un paiement programmé one-off. */
    private TransactionFrequence frequence;

    /** Périodicité (en unités de fréquence), minimum 1. */
    @Min(1)
    private Integer periodicite;

    /** Alias du bénéficiaire — exclusif avec iban/othr. */
    private String alias;

    /** IBAN du bénéficiaire — doit être accompagné de payePSP. */
    private String iban;

    /** Autre référence de compte — doit être accompagnée de payePSP. */
    private String othr;

    /** Code PSP du bénéficiaire (requis avec iban ou othr). */
    private String payePSP;

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
        if ((hasIban || hasOthr) && !hasPsp) return false;
        return true;
    }

    @JsonIgnore
    @AssertTrue(message = "Pour un abonnement, 'periodicite' est obligatoire lorsque 'frequence' est renseignée")
    public boolean isPeriodicityConsistent() {
        if (frequence == null) return true; // Programme one-off — OK
        return periodicite != null && periodicite >= 1;
    }

    @JsonIgnore
    @AssertTrue(message = "'dateFin' doit être postérieure à 'dateDebut'")
    public boolean isDateRangeValid() {
        if (dateFin == null || dateDebut == null) return true;
        return dateFin.isAfter(dateDebut);
    }

    /** Convenience for the service layer. */
    @JsonIgnore
    public boolean isSubscription() {
        return frequence != null;
    }
}
