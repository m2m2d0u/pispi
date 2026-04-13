package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Acknowledgment payload (ADMI.011) pushed by the AIP confirming receipt of a notification sent by this PI")
public class AccuseCallbackPayload {

    @Schema(description = "Unique message identifier of this acknowledgment", example = "MCIE001XJWACK000001")
    private String msgId;

    @Schema(description = "Message identifier of the original notification being acknowledged", example = "MCIE002XJWNOTIF00001")
    private String msgIdDemande;

    @Schema(description = "Acknowledged event type", example = "PING")
    private String evenement;
}
