package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.dto.common.DocumentInfo;
import ci.sycapay.pispi.dto.common.MerchantInfo;
import ci.sycapay.pispi.enums.CanalCommunication;
import ci.sycapay.pispi.enums.TypeCompte;
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

    private String dateHeureExecution;

    @NotBlank @Size(min = 6, max = 6)
    private String codeMembreParticipantPaye;

    @NotBlank @Size(max = 34)
    private String numeroCompteClientPayeur;

    @NotNull
    private TypeCompte typeCompteClientPayeur;

    @NotBlank @Size(max = 34)
    private String numeroCompteClientPaye;

    @NotNull
    private TypeCompte typeCompteClientPaye;

    @Valid @NotNull
    private ClientInfo clientPayeur;

    @Valid @NotNull
    private ClientInfo clientPaye;

    @Size(max = 140)
    private String motif;

    @Size(max = 35)
    private String referenceClient;

    @Valid
    private DocumentInfo document;

    @Valid
    private MerchantInfo marchand;

    private BigDecimal montantAchat;
    private BigDecimal montantRetrait;
    private BigDecimal fraisRetrait;
}
