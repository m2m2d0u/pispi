package ci.sycapay.pispi.dto.transaction;

import ci.sycapay.pispi.enums.ConfirmationMethode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Payload of {@code PUT /api/v1/transferts/{id}} — confirms an initiated
 * transaction and triggers the PACS.008 / PAIN.013 send.
 *
 * <p>The montant is re-submitted on confirm so the backend can cross-check
 * against what was captured at initiation time (defence against tampering of
 * the display amount in the mobile app).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionConfirmCommand {

    /** Date/heure de confirmation (ISO 8601). */
    @NotNull
    private OffsetDateTime confirmationDate;

    /** Méthode de confirmation (biometry | pin). */
    @NotNull
    private ConfirmationMethode confirmationMethode;

    /** Montant à re-soumettre pour vérification. */
    @NotNull
    @DecimalMin("1.0")
    private BigDecimal montant;

    /** Motif (optionnel — peut écraser celui de l'initiation). */
    private String motif;

    /** Coordonnées GPS au moment de la confirmation. */
    private BigDecimal latitude;
    private BigDecimal longitude;
}
