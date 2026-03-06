package ci.sycapay.pispi.dto.returnfunds;

import ci.sycapay.pispi.enums.CodeRaisonRetourFonds;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnAcceptRequest {

    @NotNull @Positive
    private BigDecimal montantRetourne;

    @NotNull
    private CodeRaisonRetourFonds raisonRetour;
}
