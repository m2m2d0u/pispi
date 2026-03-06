package ci.sycapay.pispi.dto.transfer;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRespondRequest {

    @NotNull
    private Boolean resultatVerification;

    private String codeRaison;
}
