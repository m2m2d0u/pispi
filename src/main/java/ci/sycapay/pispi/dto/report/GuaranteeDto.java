package ci.sycapay.pispi.dto.report;

import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.TypeOperationGarantie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuaranteeDto {

    private String msgId;
    private IsoMessageType sourceMessageType;
    private String participantSponsor;
    private BigDecimal montantGarantie;
    private BigDecimal montantRestantGarantie;
    private TypeOperationGarantie typeOperationGarantie;
    private String dateEffectiveGarantie;
    private BigDecimal montantGarantiePlafond;
    private String dateDebut;
    private String dateFin;
    private String createdAt;
}
