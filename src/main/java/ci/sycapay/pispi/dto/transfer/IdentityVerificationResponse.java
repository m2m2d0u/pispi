package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationResponse {

    private String endToEndId;
    private String msgId;
    private VerificationStatus statut;
    private Boolean resultatVerification;
    private String codeRaison;
    private String createdAt;
}
