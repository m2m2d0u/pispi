package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "System notification payload (ADMI.004) pushed by the AIP")
public class NotificationCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWNOTIF00001")
    private String msgId;

    @Schema(description = "Event type code (e.g. PING, MAINT, CLOSE)", example = "PING")
    private String evenement;

    @Schema(description = "Human-readable event description", example = "Connectivity test initiated by AIP")
    private String evenementDescription;

    @Schema(description = "Event timestamp (ISO 8601)", example = "2026-04-13T10:00:00Z")
    private String evenementDate;
}
