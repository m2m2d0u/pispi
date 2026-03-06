package ci.sycapay.pispi.dto.rtp;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RtpRejectRequest {

    @NotBlank
    private String codeRaison;
}
