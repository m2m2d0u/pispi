package ci.sycapay.pispi.dto.rtp;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.dto.common.DocumentInfo;
import ci.sycapay.pispi.dto.common.MerchantInfo;
import ci.sycapay.pispi.enums.CanalCommunicationRtp;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.enums.TypeCompteRtpPaye;
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
public class RequestToPayRequest {

    @NotNull
    private TypeTransaction typeTransaction;

    @NotNull
    private CanalCommunicationRtp canalCommunication;

    @NotBlank
    private String clientDemandeur;

    @NotBlank
    private String identifiantDemandePaiement;

    @NotNull @Positive
    private BigDecimal montant;

    private String dateHeureExecution;
    private String dateHeureLimiteAction;

    @NotBlank @Size(min = 6, max = 6)
    private String codeMembreParticipantPayeur;

    @NotBlank @Size(max = 34)
    private String numeroCompteClientPayeur;

    @NotNull
    private TypeCompte typeCompteClientPayeur;

    @NotBlank @Size(max = 34)
    private String numeroCompteClientPaye;

    @NotNull
    private TypeCompteRtpPaye typeCompteClientPaye;

    @Valid @NotNull
    private ClientInfo clientPayeur;

    @Valid @NotNull
    private ClientInfo clientPaye;

    @Size(max = 140)
    private String motif;

    @Size(max = 35)
    private String referenceClient;

    private Boolean autorisationModificationMontant;
    private BigDecimal montantRemisePaiementImmediat;
    private BigDecimal tauxRemisePaiementImmediat;

    @Size(max = 35)
    private String identifiantMandat;
    private String signatureNumeriqueMandat;

    @Valid
    private DocumentInfo document;

    @Valid
    private MerchantInfo marchand;

    private BigDecimal montantAchat;
    private BigDecimal montantRetrait;
    private BigDecimal fraisRetrait;
}
