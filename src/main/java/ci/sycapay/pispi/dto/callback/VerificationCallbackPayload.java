package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Inbound identity verification request payload (ACMT.023) pushed by the AIP")
public class VerificationCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWVERIF00001")
    private String msgId;

    @Schema(description = "End-to-end identifier", example = "E2EMCIE001XJVERIF001")
    private String endToEndId;

    @Schema(description = "Member code of the requesting participant", example = "CIE001")
    private String codeMembreParticipantPayeur;

    @Schema(description = "Member code of this PI (the verifying participant)", example = "CIE002")
    private String codeMembreParticipantPaye;

    @Schema(description = "Account number to verify", example = "CI001234567890")
    private String numeroCompteClientPaye;

    @Schema(description = "Account type (e.g. CACC, SVGS, MOMA)", example = "CACC")
    private String typeCompteClientPaye;

    @Schema(description = "Expected last name of the account holder", example = "Koné")
    private String nomClientPaye;

    @Schema(description = "Expected first name of the account holder", example = "Fatou")
    private String prenomClientPaye;
}
