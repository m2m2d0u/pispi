package ci.sycapay.pispi.dto.transaction;

import ci.sycapay.pispi.enums.TransactionCancelReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload of {@code PUT /api/v1/transferts/{id}/annulations} — emits a camt.056
 * cancellation request against a previously issued transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCancelCommand {

    @NotNull
    private TransactionCancelReason raison;
}
