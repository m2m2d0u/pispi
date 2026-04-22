package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Inbound identity verification request payload (ACMT.023 — schéma BCEAO {@code Identite}).
 * Poussé par l'AIP lorsqu'un autre participant demande la vérification d'un compte détenu
 * chez ce PI.
 */
@Data
@Schema(description = "Inbound ACMT.023 — BCEAO Identite schema")
public class VerificationCallbackPayload {

    @Schema(description = "Identifiant unique du message.", example = "MCIE001XJWVERIF00001")
    private String msgId;

    @Schema(description = "Identifiant bout-en-bout.", example = "ECIE001XJWE2E00001VERIF000000001")
    private String endToEndId;

    @Schema(description = "Code du participant détenteur du compte à vérifier (ce PI).",
            example = "CIE002")
    private String codeMembreParticipant;

    @Schema(description = "IBAN du compte à vérifier (banques). Exclusif avec otherClient.",
            example = "CI05CI12345678901234567890123")
    private String ibanClient;

    @Schema(description = "Autre identifiant du compte à vérifier (ex. téléphone pour EME). "
            + "Exclusif avec ibanClient.", example = "+2250707077777")
    private String otherClient;
}
