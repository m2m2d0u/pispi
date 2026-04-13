package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Return-of-funds execution payload (PACS.004) pushed by the AIP when the receiving PI accepts and sends funds back")
public class RetourFondsCallbackPayload {

    @Schema(description = "Unique message identifier of this return execution", example = "MCIE002XJWRETEXE0001")
    private String msgId;

    @Schema(description = "New end-to-end identifier for the return transfer", example = "E2EMCIE002XJRET00001")
    private String endToEndId;

    @Schema(description = "Amount returned in XOF", example = "50000.00")
    private BigDecimal montantRetourne;

    @Schema(description = "Reason code for the return (e.g. DUPL, FRAD)", example = "DUPL")
    private String raisonRetour;
}
