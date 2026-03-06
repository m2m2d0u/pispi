package ci.sycapay.pispi.dto.rtp;

import ci.sycapay.pispi.enums.RtpStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestToPayResponse {

    private String endToEndId;
    private String msgId;
    private String identifiantDemandePaiement;
    private RtpStatus statut;
    private String codeRaison;
    private BigDecimal montant;
    private String codeMembreParticipantPayeur;
    private String codeMembreParticipantPaye;
    private String transferEndToEndId;
    private String createdAt;
}
