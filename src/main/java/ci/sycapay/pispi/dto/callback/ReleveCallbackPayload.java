package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Transaction statement payload (CAMT.052) pushed by the AIP in response to a CAMT.060 TRANS request")
public class ReleveCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWREL000001")
    private String msgId;

    @Schema(description = "Statement identifier grouping pages of the same report", example = "RELEVE20260413001")
    private String identifiantReleve;

    @Schema(description = "Current page number (1-based)", example = "1")
    private Integer pageCourante;

    @Schema(description = "True if this is the last page", example = "true")
    private Boolean dernierePage;

    @Schema(description = "Total number of transactions across all pages", example = "42")
    private Integer nbreTotalTransaction;

    @Schema(description = "Balance indicator (DBIT or CRDT)", example = "CRDT")
    private String indicateurSolde;

    @Schema(description = "List of transaction entries in this page")
    private List<Map<String, Object>> transactions;
}
