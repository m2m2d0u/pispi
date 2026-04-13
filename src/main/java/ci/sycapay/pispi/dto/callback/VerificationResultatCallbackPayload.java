package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Identity verification result payload (ACMT.024) pushed by the AIP")
public class VerificationResultatCallbackPayload {

    @Schema(description = "Message identifier of this result", example = "MCIE002XJWRESVER0001")
    private String msgId;

    @Schema(description = "Message identifier of the original verification request", example = "MCIE001XJWVERIF00001")
    private String msgIdDemande;

    @Schema(description = "End-to-end identifier", example = "E2EMCIE001XJVERIF001")
    private String endToEndId;

    @Schema(description = "True if the identity was verified successfully", example = "true")
    private Boolean resultatVerification;

    @Schema(description = "ISO reason code if verification failed", example = "AC01")
    private String codeRaison;
}
