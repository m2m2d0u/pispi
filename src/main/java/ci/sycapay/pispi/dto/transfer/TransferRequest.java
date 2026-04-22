package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.dto.common.DocumentInfo;
import ci.sycapay.pispi.enums.CanalCommunication;
import ci.sycapay.pispi.enums.TypeTransaction;
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

    @NotBlank @Size(min = 6, max = 6)
    private String codeMembreParticipantPaye;

    /** endToEndId d'une recherche d'alias (RAC_SEARCH) inbound pour le payeur. */
    @NotBlank
    private String endToEndIdSearchPayeur;

    /** Codification interne de l'alias du payé (ex: CIE002-MB-20260422-00001). */
    @NotBlank @Size(max = 30)
    private String codificationPaye;

    // Optional alias identifiers (when transfer is alias-based)
    private String aliasClientPayeur;
    private String aliasClientPaye;

    @Size(max = 140)
    private String motif;

    @Size(max = 35)
    private String identifiantTransaction;

    @Size(max = 35)
    private String referenceBulk;

    @Valid
    private DocumentInfo document;

    private BigDecimal montantAchat;
    private BigDecimal montantRetrait;
    private BigDecimal fraisRetrait;
}
