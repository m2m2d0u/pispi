package ci.sycapay.pispi.dto.transaction;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * {@code action = receive_now}. Request-to-pay — emits a PAIN.013 to the client
 * payeur identified by {@code alias}. Canal is always {@code 631} on the wire
 * (per spec, the backend remaps 631→500 when the local client is a commerçant
 * type C).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionDemandePaiementRequest extends TransactionInitiationRequest {

    /** Alias du client payeur (obligatoire pour une demande de paiement). */
    @NotBlank
    private String alias;
}
