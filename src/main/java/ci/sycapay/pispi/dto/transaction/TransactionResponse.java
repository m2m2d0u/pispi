package ci.sycapay.pispi.dto.transaction;

import ci.sycapay.pispi.enums.TransactionFrequence;
import ci.sycapay.pispi.enums.TransactionCancelReason;
import ci.sycapay.pispi.enums.TransactionStatut;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Flat mobile-facing projection of a transaction, matching the BCEAO remote
 * spec's {@code Transaction} schema. The service layer maps {@link ci.sycapay.pispi.entity.PiTransfer}
 * / {@link ci.sycapay.pispi.entity.PiRequestToPay} into this shape.
 *
 * <p>Nullability follows the spec — only {@code compte}, {@code montant},
 * {@code sens}, {@code clientNom}, {@code clientPays}, {@code endToEndId}
 * are required; everything else is optional and serialized only when present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    /** Numéro de compte du client connecté. */
    private String compte;

    /** Alias de compte du client connecté. */
    private String alias;

    /** Canal utilisé pour la transaction (code BCEAO : 731, 633, 631, ...). */
    private String canal;

    private BigDecimal montant;
    private BigDecimal montantFrais;

    private String endToEndId;

    /** Identifiant technique (TxId — présent pour les QR dynamiques). */
    private String txId;

    /** {@code debit} (transfert émis) ou {@code credit} (reçu). */
    private String sens;

    private String motif;

    // -- counterparty snapshot --------------------------------------------------
    private String clientNom;
    private String clientPays;
    private String clientPSP;
    private String clientPSPNom;
    private String clientPhoto;
    private String clientCompte;
    private String clientAlias;

    /** Date d'irrévocabilité (si statut = irrevocable). */
    private OffsetDateTime dateOperation;

    private TransactionStatut statut;
    private String statutRaison;

    /** Référence de facture (canaux 401/FACTURE). */
    private String facture;

    // -- programmation / abonnement -------------------------------------------
    private OffsetDateTime dateDebut;
    private OffsetDateTime dateFin;
    private TransactionFrequence frequence;
    private Integer periodicite;
    private String subscriptionId;

    // -- retour de fonds -------------------------------------------------------
    private OffsetDateTime retourDate;
    private TransactionStatut retourStatut;
    private String retourStatutRaison;

    // -- annulation ------------------------------------------------------------
    private TransactionCancelReason annulationRaison;
    private OffsetDateTime annulationDate;
    private TransactionStatut annulationStatut;
    private String annulationStatutRaison;

    // -- demande de paiement --------------------------------------------------
    private OffsetDateTime dateDemande;
    private OffsetDateTime dateReponse;
    private OffsetDateTime dateExpiration;

    // -- PICASH / PICO (retrait, remise) --------------------------------------
    private BigDecimal remise;
    private BigDecimal retraitAchat;
    private BigDecimal retraitMontant;
    private BigDecimal retraitFrais;

    // -- débit différé ---------------------------------------------------------
    private Boolean differe;
    private TransactionFrequence differeFrequence;
    private Integer differeOccurence;
    private BigDecimal differeMontant;
}
