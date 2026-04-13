package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Inbound Request-to-Pay payload (PAIN.013) pushed by the AIP")
public class DemandePaiementCallbackPayload {

    @Schema(description = "Unique message identifier", example = "MCIE001XJWRTP000001")
    private String msgId;

    @Schema(description = "End-to-end identifier", example = "E2EMCIE001XJRTP00001")
    private String endToEndId;

    @Schema(description = "Transaction type", example = "VIRT")
    private String typeTransaction;

    @Schema(description = "Communication channel code", example = "01")
    private String canalCommunication;

    @Schema(description = "Requested amount in XOF", example = "25000.00")
    private BigDecimal montant;

    @Schema(description = "Currency", example = "XOF")
    private String devise;

    @Schema(description = "Member code of the payee (requesting) participant", example = "CIE001")
    private String codeMembreParticipantPayeur;

    @Schema(description = "Member code of this PI (the payer participant)", example = "CIE002")
    private String codeMembreParticipantPaye;

    @Schema(description = "Deadline for the payer to act (ISO 8601)", example = "2026-04-13T18:00:00Z")
    private String dateHeureLimiteAction;

    @Schema(description = "Identifier of the RTP assigned by the payee PI", example = "IDRTP001CIE001X12345")
    private String identifiantDemandePaiement;
}
