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

    /** Code BIC-6 of the member PI that received the original transfer (who holds the funds). */
    @NotBlank @Size(max = 6)
    private String codeMembreParticipantPaye;

    @NotNull
    private CodeRaisonDemandeRetourFonds raison;
}
