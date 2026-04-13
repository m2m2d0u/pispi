package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Inbound return-of-funds request payload (CAMT.056) pushed by the AIP")
public class RetourFondsDemandeCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWRET000001")
    private String msgId;

    @Schema(description = "End-to-end identifier of the original transfer", example = "E2EMCIE001XJABCD1234")
    private String endToEndId;

    @Schema(description = "Unique return request identifier assigned by the requesting PI", example = "RETCIE001XJABCD12345")
    private String identifiantDemandeRetourFonds;

    @Schema(description = "Reason code for the return request (e.g. DUPL, FRAD, TECH)", example = "DUPL")
    private String raison;
}
