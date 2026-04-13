package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request-to-Pay rejection result payload (PAIN.014) pushed by the AIP")
public class DemandePaiementResultatCallbackPayload {

    @Schema(description = "Message identifier of this result", example = "MCIE002XJWRESRTP0001")
    private String msgId;

    @Schema(description = "Message identifier of the original RTP", example = "MCIE001XJWRTP000001")
    private String msgIdDemande;

    @Schema(description = "End-to-end identifier", example = "E2EMCIE001XJRTP00001")
    private String endToEndId;

    @Schema(description = "Final status (RJCT = rejected)", example = "RJCT")
    private String statutDemandePaiement;

    @Schema(description = "Rejection reason code", example = "MS03")
    private String codeRaison;
}
