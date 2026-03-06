package ci.sycapay.pispi.dto.returnfunds;

import ci.sycapay.pispi.enums.CodeRaisonDemandeRetourFonds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnFundsRequest {

    @NotBlank @Size(max = 35)
    private String endToEndId;

    @NotNull
    private CodeRaisonDemandeRetourFonds raison;
}
