package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Guarantee/collateral update payload (CAMT.010) pushed by the AIP")
public class GarantieCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWGAR000001")
    private String msgId;

    @Schema(description = "Current guarantee amount in XOF", example = "5000000.00")
    private BigDecimal montantGarantie;

    @Schema(description = "Remaining available guarantee amount in XOF", example = "3500000.00")
    private BigDecimal montantRestantGarantie;

    @Schema(description = "Type of guarantee operation (e.g. BLOC, DEBLO, AJST)", example = "AJST")
    private String typeOperationGarantie;

    @Schema(description = "Effective date and time of the guarantee operation (ISO 8601)", example = "2026-04-13T08:00:00Z")
    private String dateEffectiveGarantie;

    @Schema(description = "Sponsor member code", example = "SPONCIE001")
    private String participantSponsor;
}
