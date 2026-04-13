package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Compensation/settlement balances payload (CAMT.053) pushed by the AIP in response to a CAMT.060 COMP request")
public class CompensationCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWCOMP000001")
    private String msgId;

    @Schema(description = "List of settlement balance entries")
    private List<SoldeEntry> soldes;

    @Data
    @Schema(description = "Individual settlement balance entry")
    public static class SoldeEntry {

        @Schema(description = "Balance entry identifier", example = "SOLDE20260413001")
        private String id;

        @Schema(description = "Member code of the participant", example = "CIE002")
        private String participant;

        @Schema(description = "Sponsor member code", example = "SPONCIE001")
        private String participantSponsor;

        @Schema(description = "Balance type (e.g. CLSG, OPNG)", example = "CLSG")
        private String balanceType;

        @Schema(description = "Balance amount in XOF", example = "1500000.00")
        private BigDecimal montant;

        @Schema(description = "Balance direction (DBIT or CRDT)", example = "CRDT")
        private String operationType;

        @Schema(description = "Balance timestamp (ISO 8601)", example = "2026-04-13T00:00:00Z")
        private String dateBalance;
    }
}
