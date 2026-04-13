package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Sponsor/guarantee relation update payload (REDA.017) pushed by the AIP when ceiling amount or validity dates change")
public class RelationCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWREL000001")
    private String msgId;

    @Schema(description = "New guarantee ceiling amount in XOF", example = "10000000.00")
    private BigDecimal montantGarantiePlafond;

    @Schema(description = "Start date of the relation validity (YYYY-MM-DD)", example = "2026-01-01")
    private String dateDebut;

    @Schema(description = "End date of the relation validity (YYYY-MM-DD)", example = "2026-12-31")
    private String dateFin;

    @Schema(description = "Sponsor member code", example = "SPONCIE001")
    private String participantSponsor;
}
