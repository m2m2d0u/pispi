package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Message rejection payload (ADMI.002) pushed by the AIP when a submitted message is structurally invalid")
public class RejetCallbackPayload {

    @Schema(description = "Message identifier of the rejected message", example = "MCIE002XJW4A6XLMLE6Q")
    private String msgId;

    @Schema(description = "Rejection reason code", example = "NARR")
    private String codeRaisonRejet;

    @Schema(description = "Human-readable rejection description", example = "Invalid message format")
    private String descriptionRejet;
}
