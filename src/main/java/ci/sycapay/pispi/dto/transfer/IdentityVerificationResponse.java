package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API response for an identity verification record (local view).
 * Exposes the rich BCEAO {@code IdentiteReponse} fields once the verification completes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationResponse {

    private String endToEndId;
    private String msgId;
    private VerificationStatus statut;

    // ACMT.023 request metadata
    private String codeMembreParticipant;
    private String ibanClient;
    private String otherClient;

    // ACMT.024 result
    private Boolean resultatVerification;
    private String codeRaison;

    // Client details populated when resultatVerification = true
    private TypeCompte typeCompte;
    private TypeClient typeClient;
    private String nomClient;
    private String villeClient;
    private String adresseComplete;
    private String numeroIdentification;
    private CodeSystemeIdentification systemeIdentification;
    private String numeroRCCMClient;
    private String identificationFiscaleCommercant;
    private String dateNaissance;
    private String villeNaissance;
    private String paysNaissance;
    private String paysResidence;
    private String devise;

    private String createdAt;
}
