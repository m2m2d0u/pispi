package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Invoice/billing report payload (CAMT.086) pushed by the AIP in response to a CAMT.060 FACT request")
public class FactureCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWFACT000001")
    private String msgId;

    @Schema(description = "List of invoice groups, each containing individual invoice lines")
    private List<GroupeFacture> listeGroupeFacture;

    @Data
    @Schema(description = "Group of invoices from one sender to one receiver")
    public static class GroupeFacture {

        @Schema(description = "Group identifier", example = "GRP20260413001")
        private String groupeId;

        @Schema(description = "Name of the billing sender", example = "BCEAO")
        private String senderName;

        @Schema(description = "Identifier of the billing sender", example = "BCEAOSN")
        private String senderId;

        @Schema(description = "Name of the billed participant", example = "Sycapay CI")
        private String receiverName;

        @Schema(description = "Member code of the billed participant", example = "CIE002")
        private String receiverId;

        @Schema(description = "List of individual invoice statements in this group")
        private List<Map<String, Object>> listeFacture;
    }
}
