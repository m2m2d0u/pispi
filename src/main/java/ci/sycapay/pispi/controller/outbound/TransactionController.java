package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.transaction.IncomingTransferRejectCommand;
import ci.sycapay.pispi.dto.transaction.TransactionCancelCommand;
import ci.sycapay.pispi.dto.transaction.TransactionConfirmCommand;
import ci.sycapay.pispi.dto.transaction.TransactionInitiationRequest;
import ci.sycapay.pispi.dto.transaction.TransactionRejectCommand;
import ci.sycapay.pispi.dto.transaction.TransactionResponse;
import ci.sycapay.pispi.service.transaction.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile-facing transfer endpoints, aligned on the BCEAO remote spec
 * ({@code documentation/openapi-bceao-remote.json}, tag {@code Gestion des transactions}).
 *
 * <p>This is the sole mobile-facing transfer surface — the legacy
 * {@code /api/v1/transfers} controller has been retired and all traffic now
 * goes through the two-phase initiate → confirm flow on this controller.
 * Legacy inbound-transfer handling (PACS.008 callbacks, PACS.002 replies) is
 * still owned by {@code TransferCallbackController}; the RTP side continues
 * to have its own initiator at {@code /api/v1/request-to-pay}.
 */
@Tag(name = "Transactions (mobile)",
        description = "Unified transaction endpoints per BCEAO remote spec — "
                + "send_now (transfert), receive_now (demande de paiement), "
                + "send_schedule (paiement programmé / abonnement)")
@Slf4j
@RestController
@RequestMapping("/api/v1/transferts")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Initier un transfert",
            description = "Crée une transaction en statut 'initie'. Aucun message ISO 20022 "
                    + "n'est émis à ce stade — la confirmation ultérieure (PUT) déclenche le "
                    + "PACS.008 (send_now) ou PAIN.013 (receive_now). Supporte les trois modes "
                    + "d'identification du bénéficiaire : alias, iban+payePSP, othr+payePSP.")
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> initier(
            @Valid @RequestBody TransactionInitiationRequest request) {
        TransactionResponse data = transactionService.initiate(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Confirmer un transfert / accepter une demande de paiement",
            description = "Confirme une transaction initiée (biométrie ou PIN). Déclenche "
                    + "l'émission du PACS.008 (send_now) ou PAIN.013 (receive_now) vers l'AIP.")
    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> confirmer(
            @Parameter(description = "endToEndId de la transaction") @PathVariable("id") String id,
            @Valid @RequestBody TransactionConfirmCommand command) {
        return ApiResponse.ok(transactionService.confirm(id, command));
    }

    @Operation(summary = "Récupérer une transaction",
            description = "Renvoie l'état d'une transaction identifiée par son endToEndId.")
    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> recuperer(
            @Parameter(description = "endToEndId de la transaction") @PathVariable("id") String id) {
        return ApiResponse.ok(transactionService.getById(id));
    }

    @Operation(summary = "Lister les transactions",
            description = "Renvoie la page de transactions émises, triées par date de création décroissante.")
    @GetMapping
    public ApiResponse<Page<TransactionResponse>> lister(Pageable pageable) {
        return ApiResponse.ok(transactionService.list(pageable));
    }

    @Operation(summary = "Demander l'annulation d'un transfert émis",
            description = "Émet une demande camt.056 pour annuler un transfert déjà irrévocable.")
    @PutMapping("/{id}/annulations")
    public ResponseEntity<ApiResponse<Void>> annuler(
            @Parameter(description = "endToEndId de la transaction") @PathVariable("id") String id,
            @Valid @RequestBody TransactionCancelCommand command) {
        transactionService.cancel(id, command);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @Operation(summary = "Retourner les fonds d'un transfert reçu",
            description = "Émet un pacs.004 pour retourner les fonds d'une transaction créditée.")
    @PutMapping("/{id}/retours")
    public ResponseEntity<ApiResponse<Void>> retourner(
            @Parameter(description = "endToEndId de la transaction") @PathVariable("id") String id) {
        transactionService.returnFunds(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @Operation(summary = "Rejeter une demande (paiement ou annulation)",
            description = "Émet un pain.014 (rejet de demande de paiement) ou camt.029 "
                    + "(rejet de demande d'annulation).")
    @PutMapping("/{id}/rejets")
    public ApiResponse<TransactionResponse> rejeter(
            @Parameter(description = "endToEndId de la transaction") @PathVariable("id") String id,
            @Valid @RequestBody TransactionRejectCommand command) {
        return ApiResponse.ok(transactionService.reject(id, command));
    }

    @Operation(summary = "Désactiver un paiement programmé / abonnement",
            description = "Stoppe les exécutions futures d'un send_schedule (Programme ou Abonnement). "
                    + "L'endToEndId doit référencer la ligne parente SEND_SCHEDULE, pas une "
                    + "exécution déjà émise.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> desactiver(
            @Parameter(description = "endToEndId du planning") @PathVariable("id") String id) {
        transactionService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Accepter un PACS.008 entrant (émet PACS.002 ACCC)",
            description = "BCEAO §4.3 : « Un participant payé reçoit un ordre de transfert "
                    + "(pacs.008) et doit retourner un pacs.002 qui précisera le traitement. » "
                    + "Cet endpoint émet le PACS.002 d'acceptation pour un transfert entrant en "
                    + "statut PEND. La ligne locale passe à ACCC et porte la dateHeureIrrevocabilite "
                    + "du moment de l'acceptation. À appeler par le backend après que le compte "
                    + "du payé a été crédité.")
    @PostMapping("/incoming/{id}/accept")
    public ApiResponse<TransactionResponse> accepterEntrant(
            @Parameter(description = "endToEndId du transfert entrant") @PathVariable("id") String id) {
        return ApiResponse.ok(transactionService.acceptIncomingTransfer(id));
    }

    @Operation(summary = "Rejeter un PACS.008 entrant (émet PACS.002 RJCT)",
            description = "Émet un PACS.002 de rejet vers l'AIP avec un codeRaison BCEAO "
                    + "(pattern [A-Z]{2}\\d{2}, ex: AC01, AC04, BE01, FR01, MS03). La ligne "
                    + "locale passe à RJCT.")
    @PostMapping("/incoming/{id}/reject")
    public ApiResponse<TransactionResponse> rejeterEntrant(
            @Parameter(description = "endToEndId du transfert entrant") @PathVariable("id") String id,
            @Valid @RequestBody IncomingTransferRejectCommand command) {
        return ApiResponse.ok(transactionService.rejectIncomingTransfer(id, command));
    }
}
