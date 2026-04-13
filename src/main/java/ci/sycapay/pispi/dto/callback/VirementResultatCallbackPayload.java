package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Transfer accept/reject outcome payload (PACS.002) pushed by the AIP")
public class VirementResultatCallbackPayload {

    @Schema(description = "Message identifier of this response", example = "MCIE001XJW4BRES12345")
    private String msgId;

    @Schema(description = "Message identifier of the original transfer request", example = "MCIE002XJW4A6XLMLE6Q")
    private String msgIdDemande;

    @Schema(description = "End-to-end identifier", example = "E2EMCIE002XJABCD1234")
    private String endToEndId;

    @Schema(description = "Final status of the transaction (ACCC = accepted, RJCT = rejected)", example = "ACCC")
    private String statutTransaction;

    @Schema(description = "ISO reason code if rejected", example = "AC01")
    private String codeRaison;

    @Schema(description = "Irrevocability timestamp (ISO 8601)", example = "2026-04-13T10:15:00Z")
    private String dateHeureIrrevocabilite;
}
