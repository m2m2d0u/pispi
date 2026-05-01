package ci.sycapay.pispi.dto.returnfunds;

import ci.sycapay.pispi.enums.CodeRaisonRetourFonds;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRejectRequest {

    @NotNull
    private CodeRaisonRetourFonds raison;
}
