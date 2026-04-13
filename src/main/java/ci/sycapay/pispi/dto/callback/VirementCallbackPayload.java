package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Inbound credit transfer payload (PACS.008) pushed by the AIP")
public class VirementCallbackPayload {

    @Schema(description = "Unique message identifier assigned by the AIP", example = "MCIE002XJW4A6XLMLE6Q")
    private String msgId;

    @Schema(description = "End-to-end identifier linking all messages of this transaction", example = "E2EMCIE002XJABCD1234")
    private String endToEndId;

    @Schema(description = "Transaction amount in XOF", example = "50000.00")
    private BigDecimal montant;

    @Schema(description = "Currency code", example = "XOF")
    private String devise;

    @Schema(description = "SPI member code of the sending participant", example = "CIE001")
    private String codeMembreParticipantPayeur;

    @Schema(description = "SPI member code of the receiving participant (this PI)", example = "CIE002")
    private String codeMembreParticipantPaye;

    @Schema(description = "Transaction type (e.g. VIRT, RETR)", example = "VIRT")
    private String typeTransaction;

    @Schema(description = "Communication channel code", example = "01")
    private String canalCommunication;

    @Schema(description = "Last name of the payer", example = "Diallo")
    private String nomClientPayeur;

    @Schema(description = "Last name of the payee", example = "Koné")
    private String nomClientPaye;
}
