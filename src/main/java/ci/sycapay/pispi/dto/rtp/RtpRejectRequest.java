package ci.sycapay.pispi.dto.rtp;

import ci.sycapay.pispi.enums.CodeRaisonRejetRtp;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RtpRejectRequest {

    @NotNull
    private CodeRaisonRejetRtp codeRaison;
}
