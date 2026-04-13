package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Return-of-funds rejection payload (CAMT.029) pushed by the AIP")
public class RetourFondsRejetCallbackPayload {

    @Schema(description = "Message identifier of this rejection", example = "MCIE002XJWRETREJ0001")
    private String msgId;

    @Schema(description = "Message identifier of the original CAMT.056 request", example = "MCIE001XJWRET000001")
    private String msgIdDemande;

    @Schema(description = "Return request identifier", example = "RETCIE001XJABCD12345")
    private String identifiantDemandeRetourFonds;

    @Schema(description = "Rejection reason code (e.g. NOAS, LEGL)", example = "NOAS")
    private String raison;
}
