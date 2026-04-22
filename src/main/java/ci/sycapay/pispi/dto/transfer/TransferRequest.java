package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.dto.common.DocumentInfo;
import ci.sycapay.pispi.enums.CanalCommunication;
import ci.sycapay.pispi.enums.TypeTransaction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotNull
    private TypeTransaction typeTransaction;

    @NotNull
    private CanalCommunication canalCommunication;

    @NotNull @Positive
    private BigDecimal montant;

    /** endToEndId d'une recherche d'alias (RAC_SEARCH) inbound pour le payeur. */
    @NotBlank
    private String endToEndIdSearchPayeur;

    /**
     * Codification interne de l'alias du payé (ex: CIE002-MB-20260422-00001).
     * Non requis pour DISP : le payeur et le payé sont la même personne.
     */
    @Size(max = 30)
    private String codificationPaye;

    @Size(max = 140)
    private String motif;

    @Size(max = 35)
    private String identifiantTransaction;

    @Size(max = 35)
    private String referenceBulk;

    @Valid
    private DocumentInfo document;

    /** GPS latitude du payeur — obligatoire pour les canaux QR, marchand, e-commerce. */
    @Size(max = 20)
    private String latitudePayeur;

    /** GPS longitude du payeur — obligatoire pour les canaux QR, marchand, e-commerce. */
    @Size(max = 20)
    private String longitudePayeur;

    private BigDecimal montantAchat;
    private BigDecimal montantRetrait;
    private BigDecimal fraisRetrait;

    @JsonIgnore
    @AssertTrue(message = "codificationPaye est requis pour les virements non-DISP")
    public boolean isCodificationPayeRequiredForNonDisp() {
        if (typeTransaction == TypeTransaction.DISP) return true;
        return codificationPaye != null && !codificationPaye.isBlank();
    }
}
