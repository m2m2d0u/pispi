package ci.sycapay.pispi.dto.transaction;

import ci.sycapay.pispi.enums.TransactionRejectReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload of {@code PUT /api/v1/transferts/{id}/rejets} — used to reject either
 * an inbound payment request (pain.014) or an inbound cancellation request
 * (camt.029). The {@code raison} enum constrains which codes are valid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRejectCommand {

    @NotNull
    private TransactionRejectReason raison;
}
