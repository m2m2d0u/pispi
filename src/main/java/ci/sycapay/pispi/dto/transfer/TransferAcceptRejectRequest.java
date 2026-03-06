package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.enums.StatutTransaction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferAcceptRejectRequest {

    @NotNull
    private StatutTransaction statutTransaction;

    private String codeRaison;
}
