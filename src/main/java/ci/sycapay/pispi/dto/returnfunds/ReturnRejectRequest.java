package ci.sycapay.pispi.dto.returnfunds;

import ci.sycapay.pispi.enums.CodeRaisonRejetDemandeRetourFonds;
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
    private CodeRaisonRejetDemandeRetourFonds raison;
}
