package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    private String endToEndId;
    private String msgId;
    private TransferStatus statut;
    private String codeRaison;
    private BigDecimal montant;
    private String codeMembreParticipantPayeur;
    private String codeMembreParticipantPaye;
    private String dateHeureIrrevocabilite;
    private String createdAt;
}
