package ci.sycapay.pispi.service.transaction;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnRejectRequest;
import ci.sycapay.pispi.dto.transaction.TransactionCancelCommand;
import ci.sycapay.pispi.dto.transaction.TransactionConfirmCommand;
import ci.sycapay.pispi.dto.transaction.TransactionImmediatRequest;
import ci.sycapay.pispi.dto.transaction.TransactionInitiationRequest;
import ci.sycapay.pispi.dto.transaction.TransactionRejectCommand;
import ci.sycapay.pispi.dto.transaction.TransactionResponse;
import ci.sycapay.pispi.dto.transaction.TransactionScheduleRequest;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.CanalCommunication;
import ci.sycapay.pispi.enums.CodeRaisonDemandeRetourFonds;
import ci.sycapay.pispi.enums.CodeRaisonRejetDemandeRetourFonds;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TransactionRejectReason;
import ci.sycapay.pispi.enums.TransactionAction;
import ci.sycapay.pispi.enums.TransactionStatut;
import ci.sycapay.pispi.enums.TransferStatus;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.enums.TypeTransaction;
import ci.sycapay.pispi.exception.InvalidStateException;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.resolver.ClientSearchResolver;
import ci.sycapay.pispi.service.resolver.ResolvedClient;
import ci.sycapay.pispi.service.returnfunds.ReturnFundsService;
import ci.sycapay.pispi.service.rtp.RequestToPayService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static ci.sycapay.pispi.util.DateTimeUtil.nowIso;

/**
 * Mobile-facing orchestrator for {@code /api/v1/transferts}, aligned on the
 * BCEAO remote spec {@code documentation/openapi-bceao-remote.json}.
 *
 * <p>Two-phase flow:
 * <ol>
 *   <li>{@link #initiate} — resolves payeur + paye from the RAC_SEARCH log,
 *       persists the full snapshot on {@link PiTransfer} in
 *       {@link TransferStatus#INITIE}, <b>emits nothing</b> to the AIP yet.</li>
 *   <li>{@link #confirm} — verifies the montant, rebuilds the PACS.008 from
 *       the stored snapshot, logs + posts it to the AIP, transitions to
 *       {@link TransferStatus#PEND}.</li>
 * </ol>
 *
 * <p><b>Wired in this phase:</b> {@code send_now} with an {@code alias}
 * beneficiary. Other actions ({@code receive_now}, {@code send_schedule})
 * and the iban+PSP / othr+PSP beneficiary modes intentionally return
 * {@code 501 Not Implemented} until their respective sub-phases land.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final PiTransferRepository transferRepository;
    private final PiReturnRequestRepository returnRequestRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;
    private final ClientSearchResolver clientSearchResolver;
    private final ReturnFundsService returnFundsService;
    private final RequestToPayService requestToPayService;

    // ------------------------------------------------------------------------
    // Initiation
    // ------------------------------------------------------------------------

    @Transactional
    public TransactionResponse initiate(TransactionInitiationRequest request) {
        return switch (request.getAction()) {
            case SEND_NOW -> initiateSendNow((TransactionImmediatRequest) request);
            case SEND_SCHEDULE -> initiateSendSchedule((TransactionScheduleRequest) request);
            case RECEIVE_NOW -> throw new UnsupportedOperationException(
                    "receive_now (demande de paiement) sera implémenté prochainement — "
                            + "utilisez /api/v1/request-to-pay pour le moment");
        };
    }

    private TransactionResponse initiateSendNow(TransactionImmediatRequest request) {
        if (request.getIban() != null || request.getOthr() != null) {
            throw new UnsupportedOperationException(
                    "Les modes 'iban + payePSP' et 'othr + payePSP' seront implémentés "
                            + "prochainement. Pour le moment, utilisez le mode 'alias'.");
        }
        if (request.getAlias() == null || request.getAlias().isBlank()) {
            throw new IllegalArgumentException(
                    "Le mode 'alias' est obligatoire en Phase 3.2 (iban/othr à venir)");
        }

        // Resolve both sides. The payer comes from the endToEndIdSearchPayeur
        // bridge (see DTO javadoc — transitional until OAuth is wired), the
        // payee comes from the latest RAC_SEARCH matching the alias.
        ResolvedClient payeur = clientSearchResolver.resolve(
                request.getEndToEndIdSearchPayeur(), "payeur");
        ResolvedClient paye = clientSearchResolver.resolveByAlias(request.getAlias(), "paye");

        CanalCommunication canal = resolveCanal(request.getCanal());
        validateLocalisationRules(canal, request);

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);
        String identifiantTransaction = resolveIdentifiantTransaction(
                canal, request.getTxId(), endToEndId);

        PiTransfer transfer = PiTransfer.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(TypeTransaction.PRMG)
                .canalCommunication(canal)
                .montant(request.getMontant())
                .devise("XOF")
                // Payeur snapshot
                .codeMembrePayeur(codeMembre)
                .numeroComptePayeur(request.getCompte())
                .typeComptePayeur(payeur.typeCompte())
                .nomClientPayeur(payeur.clientInfo().getNom())
                .prenomClientPayeur(payeur.clientInfo().getPrenom())
                .typeClientPayeur(payeur.clientInfo().getTypeClient())
                .telephonePayeur(payeur.clientInfo().getTelephone())
                .paysClientPayeur(payeur.clientInfo().getPays())
                .identifiantClientPayeur(payeur.clientInfo().getIdentifiant())
                .typeIdentifiantClientPayeur(payeur.clientInfo().getTypeIdentifiant())
                .aliasPayeur(payeur.aliasValue())
                .ibanClientPayeur(payeur.iban())
                .identificationFiscaleCommercantPayeur(payeur.identificationFiscaleCommercant())
                .identificationRccmClientPayeur(payeur.identificationRccm())
                .villeClientPayeur(payeur.clientInfo().getVille())
                .adresseClientPayeur(payeur.clientInfo().getAdresse())
                .racSearchRefPayeur(request.getEndToEndIdSearchPayeur())
                // Payé snapshot
                .codeMembrePaye(paye.codeMembre())
                .numeroComptePaye(paye.other())
                .typeComptePaye(paye.typeCompte())
                .nomClientPaye(paye.clientInfo().getNom())
                .prenomClientPaye(paye.clientInfo().getPrenom())
                .typeClientPaye(paye.clientInfo().getTypeClient())
                .telephonePaye(paye.clientInfo().getTelephone())
                .paysClientPaye(paye.clientInfo().getPays())
                .identifiantClientPaye(paye.clientInfo().getIdentifiant())
                .typeIdentifiantClientPaye(paye.clientInfo().getTypeIdentifiant())
                .aliasPaye(paye.aliasValue())
                .ibanClientPaye(paye.iban())
                .identificationFiscaleCommercantPaye(paye.identificationFiscaleCommercant())
                .identificationRccmClientPaye(paye.identificationRccm())
                .villeClientPaye(paye.clientInfo().getVille())
                .adresseClientPaye(paye.clientInfo().getAdresse())
                // Initiation metadata
                .motif(request.getMotif())
                .identifiantTransaction(identifiantTransaction)
                .latitudeClientPayeur(request.getLatitude())
                .longitudeClientPayeur(request.getLongitude())
                .dateHeureExecution(LocalDateTime.now())
                .statut(TransferStatus.INITIE)
                .build();
        transferRepository.save(transfer);

        log.info("Transaction INITIE persisted [endToEndId={}, alias={}, canal={}, txId={}]",
                endToEndId, request.getAlias(), canal, identifiantTransaction);
        return toResponse(transfer);
    }

    // ------------------------------------------------------------------------
    // Initiation — send_schedule (Programme + Abonnement)
    // ------------------------------------------------------------------------

    /**
     * Persist the schedule recipe. The parent row carries {@code action=SEND_SCHEDULE}
     * and is never emitted to the AIP — the Phase-3.4b scheduler picks it up on
     * {@code next_execution_date} and spawns child {@link PiTransfer} rows
     * (one per execution) with {@code parent_schedule_id} set back to this id.
     *
     * <p>Programme (one-off) vs Abonnement (recurring) is derived from
     * {@link TransactionScheduleRequest#isSubscription()} — the presence of
     * {@code frequence}. Abonnement with {@code dateFin == null} means
     * open-ended; {@code dateFin} set means the scheduler deactivates the
     * schedule once {@code nextExecutionDate > dateFin}.
     */
    @Transactional
    public TransactionResponse initiateSendSchedule(TransactionScheduleRequest request) {
        if (request.getIban() != null || request.getOthr() != null) {
            throw new UnsupportedOperationException(
                    "Les modes 'iban + payePSP' et 'othr + payePSP' pour send_schedule "
                            + "seront implémentés avec la prise en charge iban/othr en send_now.");
        }
        if (request.getAlias() == null || request.getAlias().isBlank()) {
            throw new IllegalArgumentException(
                    "Le mode 'alias' est obligatoire en Phase 3.4a (iban/othr à venir)");
        }
        if (request.getDateDebut() == null) {
            throw new IllegalArgumentException("'dateDebut' est obligatoire pour un send_schedule");
        }

        ResolvedClient payeur = clientSearchResolver.resolve(
                request.getEndToEndIdSearchPayeur(), "payeur");
        ResolvedClient paye = clientSearchResolver.resolveByAlias(request.getAlias(), "paye");

        CanalCommunication canal = resolveCanal(request.getCanal());

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);
        // The subscription_id the mobile app uses to refer to this recipe is
        // the same as the endToEndId — simpler than a second identifier.
        String subscriptionId = endToEndId;

        LocalDateTime dateDebut = request.getDateDebut()
                .withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime dateFin = request.getDateFin() != null
                ? request.getDateFin().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
                : null;

        PiTransfer schedule = PiTransfer.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(TypeTransaction.PRMG)
                .canalCommunication(canal)
                .montant(request.getMontant())
                .devise("XOF")
                // Payeur snapshot
                .codeMembrePayeur(codeMembre)
                .numeroComptePayeur(request.getCompte())
                .typeComptePayeur(payeur.typeCompte())
                .nomClientPayeur(payeur.clientInfo().getNom())
                .prenomClientPayeur(payeur.clientInfo().getPrenom())
                .typeClientPayeur(payeur.clientInfo().getTypeClient())
                .telephonePayeur(payeur.clientInfo().getTelephone())
                .paysClientPayeur(payeur.clientInfo().getPays())
                .identifiantClientPayeur(payeur.clientInfo().getIdentifiant())
                .typeIdentifiantClientPayeur(payeur.clientInfo().getTypeIdentifiant())
                .aliasPayeur(payeur.aliasValue())
                .ibanClientPayeur(payeur.iban())
                .identificationFiscaleCommercantPayeur(payeur.identificationFiscaleCommercant())
                .identificationRccmClientPayeur(payeur.identificationRccm())
                .villeClientPayeur(payeur.clientInfo().getVille())
                .adresseClientPayeur(payeur.clientInfo().getAdresse())
                .racSearchRefPayeur(request.getEndToEndIdSearchPayeur())
                // Payé snapshot
                .codeMembrePaye(paye.codeMembre())
                .numeroComptePaye(paye.other())
                .typeComptePaye(paye.typeCompte())
                .nomClientPaye(paye.clientInfo().getNom())
                .prenomClientPaye(paye.clientInfo().getPrenom())
                .typeClientPaye(paye.clientInfo().getTypeClient())
                .telephonePaye(paye.clientInfo().getTelephone())
                .paysClientPaye(paye.clientInfo().getPays())
                .identifiantClientPaye(paye.clientInfo().getIdentifiant())
                .typeIdentifiantClientPaye(paye.clientInfo().getTypeIdentifiant())
                .aliasPaye(paye.aliasValue())
                .ibanClientPaye(paye.iban())
                .identificationFiscaleCommercantPaye(paye.identificationFiscaleCommercant())
                .identificationRccmClientPaye(paye.identificationRccm())
                .villeClientPaye(paye.clientInfo().getVille())
                .adresseClientPaye(paye.clientInfo().getAdresse())
                // Initiation metadata
                .motif(request.getMotif())
                .latitudeClientPayeur(request.getLatitude())
                .longitudeClientPayeur(request.getLongitude())
                .dateHeureExecution(LocalDateTime.now())
                // Schedule metadata
                .action(TransactionAction.SEND_SCHEDULE)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .frequence(request.getFrequence())
                .periodicite(request.getPeriodicite())
                .nextExecutionDate(dateDebut.toLocalDate())
                .active(Boolean.TRUE)
                .subscriptionId(subscriptionId)
                .statut(TransferStatus.INITIE)
                .build();
        transferRepository.save(schedule);

        log.info("Schedule ({}) persisted [endToEndId={}, dateDebut={}, frequence={}, periodicite={}]",
                request.isSubscription() ? "Abonnement" : "Programme",
                endToEndId, dateDebut, request.getFrequence(), request.getPeriodicite());
        return toResponse(schedule);
    }

    // ------------------------------------------------------------------------
    // Deactivate a scheduled/subscription — stops future executions
    // ------------------------------------------------------------------------

    @Transactional
    public void deactivate(String endToEndId) {
        PiTransfer schedule = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", endToEndId));

        if (schedule.getAction() != TransactionAction.SEND_SCHEDULE) {
            throw new InvalidStateException(
                    "La transaction " + endToEndId + " n'est pas un paiement programmé / "
                            + "abonnement — rien à désactiver.");
        }
        if (Boolean.FALSE.equals(schedule.getActive())) {
            throw new InvalidStateException(
                    "Le planning " + endToEndId + " est déjà désactivé.");
        }

        schedule.setActive(Boolean.FALSE);
        schedule.setStatut(TransferStatus.ECHEC); // neutral terminal — no more executions
        transferRepository.save(schedule);
        log.info("Schedule désactivé [endToEndId={}]", endToEndId);
    }

    // ------------------------------------------------------------------------
    // Scheduled execution — called by TransactionScheduleRunner for each due
    // SEND_SCHEDULE parent row. Spawns a child PiTransfer, emits PACS.008, then
    // advances (or deactivates) the parent's next_execution_date.
    // ------------------------------------------------------------------------

    /**
     * Execute one scheduled run of a {@code SEND_SCHEDULE} parent row.
     *
     * <p>Transactional per call so a failure on one schedule doesn't poison
     * the whole batch — the runner catches and moves to the next due row.
     *
     * <ol>
     *   <li>Clone the payer + payee snapshot into a new child {@link PiTransfer}
     *       with a fresh msgId / endToEndId, {@code action = SEND_NOW},
     *       {@code parent_schedule_id} pointing back to the parent, and
     *       {@code statut = PEND} (we're emitting now, not initie).</li>
     *   <li>Build + log + POST the PACS.008 via the existing snapshot builder
     *       (same code path as {@link #confirm}).</li>
     *   <li>Advance the parent's {@code next_execution_date} per
     *       {@code frequence} / {@code periodicite}. One-off Programme rows
     *       ({@code frequence == null}) deactivate after a single execution;
     *       Abonnement rows deactivate when {@code next > dateFin}.</li>
     * </ol>
     *
     * @return the child execution row just persisted
     */
    @Transactional
    public PiTransfer executeScheduledExecution(PiTransfer parent) {
        if (parent.getAction() != TransactionAction.SEND_SCHEDULE) {
            throw new InvalidStateException(
                    "Seules les lignes SEND_SCHEDULE peuvent être exécutées par le scheduler "
                            + "[endToEndId=" + parent.getEndToEndId() + "]");
        }
        if (!Boolean.TRUE.equals(parent.getActive())) {
            throw new InvalidStateException(
                    "Le planning " + parent.getEndToEndId() + " est inactif — rien à exécuter.");
        }

        String codeMembre = properties.getCodeMembre();
        String childMsgId = IdGenerator.generateMsgId(codeMembre);
        String childEndToEndId = IdGenerator.generateEndToEndId(codeMembre);
        // Each execution is its own transaction — generate a fresh identifiant-
        // Transaction per child (the parent recipe never had one, by design).
        String childTxId = resolveIdentifiantTransaction(
                parent.getCanalCommunication(), null, childEndToEndId);

        PiTransfer child = PiTransfer.builder()
                .msgId(childMsgId)
                .endToEndId(childEndToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(parent.getTypeTransaction())
                .canalCommunication(parent.getCanalCommunication())
                .montant(parent.getMontant())
                .devise(parent.getDevise())
                // Payeur snapshot (copied from parent)
                .codeMembrePayeur(parent.getCodeMembrePayeur())
                .numeroComptePayeur(parent.getNumeroComptePayeur())
                .typeComptePayeur(parent.getTypeComptePayeur())
                .nomClientPayeur(parent.getNomClientPayeur())
                .prenomClientPayeur(parent.getPrenomClientPayeur())
                .typeClientPayeur(parent.getTypeClientPayeur())
                .telephonePayeur(parent.getTelephonePayeur())
                .paysClientPayeur(parent.getPaysClientPayeur())
                .identifiantClientPayeur(parent.getIdentifiantClientPayeur())
                .typeIdentifiantClientPayeur(parent.getTypeIdentifiantClientPayeur())
                .aliasPayeur(parent.getAliasPayeur())
                .ibanClientPayeur(parent.getIbanClientPayeur())
                .identificationFiscaleCommercantPayeur(parent.getIdentificationFiscaleCommercantPayeur())
                .identificationRccmClientPayeur(parent.getIdentificationRccmClientPayeur())
                .villeClientPayeur(parent.getVilleClientPayeur())
                .adresseClientPayeur(parent.getAdresseClientPayeur())
                .racSearchRefPayeur(parent.getRacSearchRefPayeur())
                // Payé snapshot (copied from parent)
                .codeMembrePaye(parent.getCodeMembrePaye())
                .numeroComptePaye(parent.getNumeroComptePaye())
                .typeComptePaye(parent.getTypeComptePaye())
                .nomClientPaye(parent.getNomClientPaye())
                .prenomClientPaye(parent.getPrenomClientPaye())
                .typeClientPaye(parent.getTypeClientPaye())
                .telephonePaye(parent.getTelephonePaye())
                .paysClientPaye(parent.getPaysClientPaye())
                .identifiantClientPaye(parent.getIdentifiantClientPaye())
                .typeIdentifiantClientPaye(parent.getTypeIdentifiantClientPaye())
                .aliasPaye(parent.getAliasPaye())
                .ibanClientPaye(parent.getIbanClientPaye())
                .identificationFiscaleCommercantPaye(parent.getIdentificationFiscaleCommercantPaye())
                .identificationRccmClientPaye(parent.getIdentificationRccmClientPaye())
                .villeClientPaye(parent.getVilleClientPaye())
                .adresseClientPaye(parent.getAdresseClientPaye())
                .racSearchRefPaye(parent.getRacSearchRefPaye())
                // Operational metadata
                .motif(parent.getMotif())
                .identifiantTransaction(childTxId)
                .latitudeClientPayeur(parent.getLatitudeClientPayeur())
                .longitudeClientPayeur(parent.getLongitudeClientPayeur())
                .dateHeureExecution(LocalDateTime.now())
                // Child-specific: executed autonomously → PEND (awaiting AIP pacs.002)
                .statut(TransferStatus.PEND)
                .action(TransactionAction.SEND_NOW)
                .parentScheduleId(parent.getId())
                .subscriptionId(parent.getSubscriptionId())
                // No confirmation — this was auto-triggered
                .confirmationDate(LocalDateTime.now())
                .build();
        transferRepository.save(child);

        // Build + emit PACS.008 from the child's own snapshot
        Map<String, Object> pacs008 = buildPacs008FromSnapshot(child);
        messageLogService.log(child.getMsgId(), child.getEndToEndId(),
                IsoMessageType.PACS_008, MessageDirection.OUTBOUND, pacs008, null, null);
        log.info("Transfert payload: {}", pacs008);
        aipClient.post("/transferts", pacs008);

        log.info("Schedule execution emitted [parentEndToEndId={}, childEndToEndId={}, "
                        + "subscriptionId={}]",
                parent.getEndToEndId(), childEndToEndId, parent.getSubscriptionId());

        // Advance or deactivate the parent
        advanceOrDeactivate(parent);
        transferRepository.save(parent);

        return child;
    }

    /**
     * Decide the parent's next run after a successful execution.
     *
     * <ul>
     *   <li>Programme (frequence == null): single-shot — deactivate.</li>
     *   <li>Abonnement (frequence set): next = previous + periodicite × frequence
     *       unit (J=days, S=weeks, M=months, A=years). If {@code dateFin} is set
     *       and the advanced date passes it, deactivate.</li>
     * </ul>
     */
    private void advanceOrDeactivate(PiTransfer parent) {
        if (parent.getFrequence() == null) {
            parent.setActive(Boolean.FALSE);
            parent.setStatut(TransferStatus.ACCC); // Programme one-off — consider it "terminé OK"
            log.info("Programme one-off terminé, désactivation [endToEndId={}]",
                    parent.getEndToEndId());
            return;
        }

        int step = parent.getPeriodicite() != null ? parent.getPeriodicite() : 1;
        LocalDate prev = parent.getNextExecutionDate();
        LocalDate next = switch (parent.getFrequence()) {
            case J -> prev.plusDays(step);
            case S -> prev.plusWeeks(step);
            case M -> prev.plusMonths(step);
            case A -> prev.plusYears(step);
        };

        if (parent.getDateFin() != null
                && next.isAfter(parent.getDateFin().toLocalDate())) {
            parent.setActive(Boolean.FALSE);
            parent.setStatut(TransferStatus.ACCC);
            log.info("Abonnement arrivé à son terme (next={} > dateFin={}), désactivation "
                            + "[endToEndId={}]",
                    next, parent.getDateFin().toLocalDate(), parent.getEndToEndId());
            return;
        }

        parent.setNextExecutionDate(next);
        log.info("Abonnement programmé pour la prochaine exécution [endToEndId={}, next={}]",
                parent.getEndToEndId(), next);
    }

    // ------------------------------------------------------------------------
    // Confirmation — triggers PACS.008 emit
    // ------------------------------------------------------------------------

    @Transactional
    public TransactionResponse confirm(String endToEndId, TransactionConfirmCommand cmd) {
        PiTransfer t = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", endToEndId));

        if (t.getStatut() != TransferStatus.INITIE) {
            throw new InvalidStateException(
                    "La transaction " + endToEndId + " est en statut " + t.getStatut()
                            + " — seules les transactions INITIE peuvent être confirmées");
        }
        if (cmd.getMontant().compareTo(t.getMontant()) != 0) {
            throw new InvalidStateException(
                    "Le montant de confirmation (" + cmd.getMontant() + ") ne correspond pas "
                            + "au montant initié (" + t.getMontant() + ")");
        }

        Map<String, Object> pacs008 = buildPacs008FromSnapshot(t);
        messageLogService.log(t.getMsgId(), t.getEndToEndId(),
                IsoMessageType.PACS_008, MessageDirection.OUTBOUND, pacs008, null, null);
        log.info("Transfert payload: {}", pacs008);
        aipClient.post("/transferts", pacs008);

        t.setStatut(TransferStatus.PEND);
        t.setConfirmationDate(cmd.getConfirmationDate().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        t.setConfirmationMethode(cmd.getConfirmationMethode());
        if (cmd.getLatitude() != null)  t.setLatitudeClientPayeur(cmd.getLatitude().toPlainString());
        if (cmd.getLongitude() != null) t.setLongitudeClientPayeur(cmd.getLongitude().toPlainString());
        if (cmd.getMotif() != null && !cmd.getMotif().isBlank()) t.setMotif(cmd.getMotif());
        transferRepository.save(t);

        log.info("Transaction {} confirmée et PACS.008 émis [method={}]",
                endToEndId, cmd.getConfirmationMethode());
        return toResponse(t);
    }

    // ------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------

    public TransactionResponse getById(String endToEndId) {
        PiTransfer t = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", endToEndId));
        return toResponse(t);
    }

    public Page<TransactionResponse> list(Pageable pageable) {
        return transferRepository.findByDirection(MessageDirection.OUTBOUND, pageable)
                .map(this::toResponse);
    }

    // ------------------------------------------------------------------------
    // Cancel — emits a camt.056 demande d'annulation for an outbound transfer
    // ------------------------------------------------------------------------

    /**
     * Request the cancellation of a transfer we previously emitted. The AIP
     * forwards the camt.056 to the counter-party PSP and returns a camt.029
     * accept/reject asynchronously via the notification callbacks.
     *
     * <p>Only transfers that have actually been sent (i.e. moved past
     * {@link TransferStatus#INITIE}) can be cancelled — a freshly initiated
     * row that never confirmed is simply discarded rather than cancelled.
     */
    @Transactional
    public void cancel(String endToEndId, TransactionCancelCommand cmd) {
        PiTransfer transfer = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", endToEndId));

        if (transfer.getStatut() == TransferStatus.INITIE) {
            throw new InvalidStateException(
                    "La transaction " + endToEndId + " est encore en statut INITIE — "
                            + "rien n'a été émis à l'AIP. Supprimez-la plutôt que de la "
                            + "demander en annulation.");
        }
        if (transfer.getStatut() == TransferStatus.RJCT
                || transfer.getStatut() == TransferStatus.ECHEC) {
            throw new InvalidStateException(
                    "La transaction " + endToEndId + " est déjà en statut terminal "
                            + transfer.getStatut() + " — rien à annuler.");
        }

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String identifiantDemande = IdGenerator.generateReturnRequestId(codeMembre);

        Map<String, Object> camt056 = new HashMap<>();
        camt056.put("msgId", msgId);
        camt056.put("identifiantDemande", identifiantDemande);
        camt056.put("endToEndId", endToEndId);
        camt056.put("msgIdOriginal", transfer.getMsgId());
        camt056.put("raison", cmd.getRaison().name());
        camt056.put("codeMembreParticipantPayeur", codeMembre);
        camt056.put("codeMembreParticipantPaye", transfer.getCodeMembrePaye());

        messageLogService.log(msgId, endToEndId, IsoMessageType.CAMT_056,
                MessageDirection.OUTBOUND, camt056, null, null);
        log.info("Transfert payload: {}", camt056);
        aipClient.post("/transferts/annulations", camt056);

        log.info("Demande d'annulation émise [endToEndId={}, raison={}]",
                endToEndId, cmd.getRaison());
    }

    // ------------------------------------------------------------------------
    // Return funds — emits a camt.056 demande de retour-de-fonds for an
    // inbound transfer (delegates to the existing ReturnFundsService)
    // ------------------------------------------------------------------------

    /**
     * Return the funds of an inbound transfer we received. The remote spec
     * doesn't carry a reason code on this endpoint, so we default to
     * {@link CodeRaisonDemandeRetourFonds#SVNR} (service non rendu) — a neutral
     * mobile-initiated return. The mobile app surfaces the reason to the user
     * out-of-band; this can be refined later once the spec carries the code.
     */
    @Transactional
    public void returnFunds(String endToEndId) {
        ReturnFundsRequest req = ReturnFundsRequest.builder()
                .endToEndId(endToEndId)
                .raison(CodeRaisonDemandeRetourFonds.SVNR)
                .build();
        returnFundsService.requestReturn(req);
        log.info("Demande de retour de fonds émise [endToEndId={}]", endToEndId);
    }

    // ------------------------------------------------------------------------
    // Reject — pain.014 (RTP) or camt.029 (cancellation) based on raison
    // ------------------------------------------------------------------------

    /**
     * Reject an inbound demande.
     *
     * <ul>
     *   <li>{@code raison = CUST} → we're rejecting a {@code demande d'annulation}
     *       that a counter-party sent us. Route to the ReturnFunds flow which
     *       emits a camt.029.</li>
     *   <li>all other codes (BE05 / AM09 / APAR / RR07 / FR01) → we're rejecting
     *       an inbound RTP (pain.013) — emit a pain.014 via the existing
     *       RequestToPayService.</li>
     * </ul>
     */
    @Transactional
    public TransactionResponse reject(String endToEndId, TransactionRejectCommand cmd) {
        if (cmd.getRaison() == TransactionRejectReason.CUST) {
            PiReturnRequest ret = returnRequestRepository.findByEndToEndId(endToEndId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Demande d'annulation", endToEndId));
            ReturnRejectRequest rejectReq = ReturnRejectRequest.builder()
                    .raison(CodeRaisonRejetDemandeRetourFonds.CUST)
                    .build();
            returnFundsService.rejectReturn(ret.getIdentifiantDemande(), rejectReq);
            log.info("Demande d'annulation rejetée [endToEndId={}, raison=CUST]", endToEndId);
            // Return the shadow Transaction view of the related transfer (if any)
            return transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
                    .map(this::toResponse)
                    .orElseGet(() -> transferRepository
                            .findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                            .map(this::toResponse)
                            .orElse(null));
        }

        // Non-CUST → treat as rejecting an inbound RTP (pain.013)
        requestToPayService.rejectRtp(endToEndId, cmd.getRaison().name());
        log.info("Demande de paiement rejetée [endToEndId={}, raison={}]", endToEndId, cmd.getRaison());
        return transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
                .map(this::toResponse)
                .orElse(null);
    }

    // ------------------------------------------------------------------------
    // Helpers — canal & validation
    // ------------------------------------------------------------------------

    private CanalCommunication resolveCanal(String raw) {
        try {
            return CanalCommunication.fromCode(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidStateException("Canal inconnu: " + raw);
        }
    }

    private static final Set<CanalCommunication> CANALS_REQUIRING_LOCALISATION = Set.of(
            CanalCommunication.QR_CODE, CanalCommunication.ADRESSE_PAIEMENT,
            CanalCommunication.QR_CODE_STATIQUE, CanalCommunication.QR_CODE_DYNAMIQUE,
            CanalCommunication.FACTURE, CanalCommunication.MARCHAND_SUR_SITE,
            CanalCommunication.E_COMMERCE_LIVRAISON, CanalCommunication.E_COMMERCE_IMMEDIAT,
            CanalCommunication.PARTICULIER);

    private void validateLocalisationRules(CanalCommunication canal, TransactionImmediatRequest req) {
        if (CANALS_REQUIRING_LOCALISATION.contains(canal)
                && (req.getLatitude() == null || req.getLatitude().isBlank()
                    || req.getLongitude() == null || req.getLongitude().isBlank())) {
            throw new IllegalArgumentException(
                    "La localisation GPS (latitude, longitude) est obligatoire pour le canal "
                            + canal.name());
        }
    }

    /**
     * Canals for which the BCEAO AIP requires {@code identifiantTransaction}
     * on the PACS.008 payload. Matches the rejection message
     * <em>"TransactionIdentification est obligatoire lorsqu'il s'agit des
     * canaux: '400', '733', '500', '521', '520', '631', '401'"</em>.
     */
    private static final Set<CanalCommunication> CANALS_REQUIRING_IDENTIFIANT_TRANSACTION = Set.of(
            CanalCommunication.QR_CODE_DYNAMIQUE,    // 400
            CanalCommunication.API_BUSINESS,          // 733
            CanalCommunication.MARCHAND_SUR_SITE,     // 500
            CanalCommunication.E_COMMERCE_IMMEDIAT,   // 521
            CanalCommunication.E_COMMERCE_LIVRAISON,  // 520
            CanalCommunication.PARTICULIER,           // 631
            CanalCommunication.FACTURE);              // 401

    /**
     * Pick (or generate) the {@code identifiantTransaction} that will be
     * carried on the PACS.008 ({@code <PmtId>/<TxId>}).
     *
     * <p>Priority:
     * <ol>
     *   <li>caller-supplied {@code txId} (mobile app scanned a QR, POS
     *       terminal emitted one, e-commerce order number, etc.)</li>
     *   <li>auto-generated when the canal requires it but the client didn't
     *       provide one — we derive a short unique value from the endToEndId
     *       tail so it's deterministic-per-transaction and stays within the
     *       35-char BCEAO limit</li>
     *   <li>{@code null} when the canal doesn't require it (optional per spec)</li>
     * </ol>
     */
    private String resolveIdentifiantTransaction(CanalCommunication canal,
                                                  String callerTxId,
                                                  String endToEndId) {
        if (callerTxId != null && !callerTxId.isBlank()) {
            return callerTxId.length() > 35 ? callerTxId.substring(0, 35) : callerTxId;
        }
        if (!CANALS_REQUIRING_IDENTIFIANT_TRANSACTION.contains(canal)) {
            return null;
        }
        // Auto-generate: last 20 chars of endToEndId prefixed by "TX" — unique
        // per transaction, 22 chars total, stays inside the 35-char cap.
        String tail = endToEndId.substring(Math.max(0, endToEndId.length() - 20));
        return "TX" + tail;
    }

    // ------------------------------------------------------------------------
    // PACS.008 payload rebuild from snapshot
    // ------------------------------------------------------------------------

    private Map<String, Object> buildPacs008FromSnapshot(PiTransfer t) {
        Map<String, Object> p = new HashMap<>();
        p.put("msgId", t.getMsgId());
        p.put("endToEndId", t.getEndToEndId());
        p.put("canalCommunication", t.getCanalCommunication().getCode());
        p.put("codeMembreParticipantPayeur", t.getCodeMembrePayeur());
        p.put("codeMembreParticipantPaye", t.getCodeMembrePaye());
        p.put("montant", t.getMontant().toBigInteger().toString());
        p.put("dateHeureAcceptation", nowIso());

        // Payeur — from snapshot
        p.put("nomClientPayeur", t.getNomClientPayeur());
        if (t.getTypeClientPayeur() != null) p.put("typeClientPayeur", t.getTypeClientPayeur().name());
        if (t.getPaysClientPayeur() != null) p.put("paysClientPayeur", t.getPaysClientPayeur());
        if (t.getTypeComptePayeur() != null) p.put("typeCompteClientPayeur", t.getTypeComptePayeur().name());
        p.put("deviseCompteClientPayeur", "XOF");
        // AccountIdentification4Choice is constrained by typeCompte in the
        // BCEAO pacs.008 schema: bank-held accounts (CACC/SVGS/TRAN/LLSV/VACC/
        // TAXE) MUST go under <IBAN>, only TRAL goes under <Othr>. Selecting
        // by "which field is non-null" flips the wrong side on and triggers
        // "Element Othr: not expected" at line 85/134 of the AIP validator.
        putAccountId(p, "ibanClientPayeur", "otherClientPayeur",
                t.getTypeComptePayeur(), t.getIbanClientPayeur(), t.getNumeroComptePayeur());
        if (t.getTypeIdentifiantClientPayeur() != null)
            p.put("systemeIdentificationClientPayeur", t.getTypeIdentifiantClientPayeur().name());
        if (t.getIdentifiantClientPayeur() != null)
            p.put("numeroIdentificationClientPayeur", t.getIdentifiantClientPayeur());
        if (t.getAliasPayeur() != null)
            p.put("aliasClientPayeur", t.getAliasPayeur());
        if (t.getIdentificationFiscaleCommercantPayeur() != null)
            p.put("identificationFiscaleCommercantPayeur", t.getIdentificationFiscaleCommercantPayeur());
        else if (t.getIdentificationRccmClientPayeur() != null)
            p.put("numeroRCCMClientPayeur", t.getIdentificationRccmClientPayeur());
        if (t.getVilleClientPayeur() != null)
            p.put("villeClientPayeur", t.getVilleClientPayeur());
        if (t.getAdresseClientPayeur() != null)
            p.put("adresseClientPayeur", t.getAdresseClientPayeur());

        // Payé — from snapshot
        p.put("nomClientPaye", t.getNomClientPaye());
        if (t.getTypeClientPaye() != null) p.put("typeClientPaye", t.getTypeClientPaye().name());
        if (t.getPaysClientPaye() != null) p.put("paysClientPaye", t.getPaysClientPaye());
        if (t.getTypeComptePaye() != null) p.put("typeCompteClientPaye", t.getTypeComptePaye().name());
        p.put("deviseCompteClientPaye", "XOF");
        putAccountId(p, "ibanClientPaye", "otherClientPaye",
                t.getTypeComptePaye(), t.getIbanClientPaye(), t.getNumeroComptePaye());
        if (t.getTypeIdentifiantClientPaye() != null)
            p.put("systemeIdentificationClientPaye", t.getTypeIdentifiantClientPaye().name());
        if (t.getIdentifiantClientPaye() != null)
            p.put("numeroIdentificationClientPaye", t.getIdentifiantClientPaye());
        if (t.getAliasPaye() != null)
            p.put("aliasClientPaye", t.getAliasPaye());
        if (t.getIdentificationFiscaleCommercantPaye() != null)
            p.put("identificationFiscaleCommercantPaye", t.getIdentificationFiscaleCommercantPaye());
        else if (t.getIdentificationRccmClientPaye() != null)
            p.put("numeroRCCMClientPaye", t.getIdentificationRccmClientPaye());
        if (t.getVilleClientPaye() != null)
            p.put("villeClientPaye", t.getVilleClientPaye());
        if (t.getAdresseClientPaye() != null)
            p.put("adresseClientPaye", t.getAdresseClientPaye());

        // Transaction-level
        if (t.getTypeTransaction() != null) p.put("typeTransaction", t.getTypeTransaction().name());
        if (t.getMotif() != null) p.put("motif", t.getMotif());
        if (t.getIdentifiantTransaction() != null)
            p.put("identifiantTransaction", t.getIdentifiantTransaction());
        if (t.getLatitudeClientPayeur() != null)
            p.put("latitudeClientPayeur", t.getLatitudeClientPayeur());
        if (t.getLongitudeClientPayeur() != null)
            p.put("longitudeClientPayeur", t.getLongitudeClientPayeur());

        return p;
    }

    // ------------------------------------------------------------------------
    // Mapping to TransactionResponse
    // ------------------------------------------------------------------------

    private TransactionResponse toResponse(PiTransfer t) {
        return TransactionResponse.builder()
                .compte(t.getNumeroComptePayeur())
                .alias(t.getAliasPayeur())
                .canal(t.getCanalCommunication() != null ? t.getCanalCommunication().getCode() : null)
                .montant(t.getMontant())
                .endToEndId(t.getEndToEndId())
                .sens(t.getDirection() == MessageDirection.OUTBOUND ? "debit" : "credit")
                .motif(t.getMotif())
                .clientNom(t.getNomClientPaye())
                .clientPays(t.getPaysClientPaye())
                .clientPSP(t.getCodeMembrePaye())
                .clientCompte(t.getIbanClientPaye() != null ? t.getIbanClientPaye() : t.getNumeroComptePaye())
                .clientAlias(t.getAliasPaye())
                .statut(mapStatut(t.getStatut()))
                .statutRaison(t.getCodeRaison())
                .dateOperation(t.getDateHeureIrrevocabilite() != null
                        ? t.getDateHeureIrrevocabilite().atOffset(ZoneOffset.UTC) : null)
                // Schedule fields (present only on SEND_SCHEDULE parent rows)
                .dateDebut(t.getDateDebut() != null
                        ? t.getDateDebut().atOffset(ZoneOffset.UTC) : null)
                .dateFin(t.getDateFin() != null
                        ? t.getDateFin().atOffset(ZoneOffset.UTC) : null)
                .frequence(t.getFrequence())
                .periodicite(t.getPeriodicite())
                .subscriptionId(t.getSubscriptionId())
                .build();
    }

    private TransactionStatut mapStatut(TransferStatus s) {
        if (s == null) return null;
        return switch (s) {
            case INITIE -> TransactionStatut.INITIE;
            case ACCC, ACSC -> TransactionStatut.IRREVOCABLE;
            case RJCT, TMOT, ECHEC -> TransactionStatut.REJETE;
            case PEND, ACSP -> TransactionStatut.INITIE;
        };
    }

    /**
     * Emit the account identifier under the schema-correct field.
     *
     * <p>BCEAO pacs.008.001.10 splits {@code AccountIdentification4Choice}
     * into three buckets:
     *
     * <ul>
     *   <li><b>Strict IBAN</b> — {@code CACC, SVGS, LLSV, VACC, TAXE}.
     *       Bank-held accounts with registered IBANs. We force {@code <IBAN>}
     *       and fall back to whichever column has data if the iban column is
     *       empty (data drift between the RAC search and our snapshot).</li>
     *   <li><b>Other</b> — {@code TRAL}. Unbanked particular; always
     *       carries the Other identifier.</li>
     *   <li><b>Prefer-IBAN, accept-Other</b> — {@code TRAN} and unknown
     *       types. The BCEAO test data for TRAN routinely carries
     *       phone-number values that wouldn't pass IBAN format validation,
     *       so we defer to the resolver's own iban-vs-other split rather
     *       than forcing a bucket.</li>
     * </ul>
     *
     * <p>Two earlier failure modes drove this shape. With a naive
     * "iban-first-else-other" check, CACC/SVGS rows whose RAC payload put
     * the IBAN under the {@code other} key emitted {@code <Othr>} where the
     * schema expects {@code <IBAN>} ({@code "Element Othr: not expected"}).
     * With a strict "all bank types → IBAN" rule, TRAN rows whose RAC
     * payload carried a phone number fell into IBAN-format validation and
     * failed with {@code "ibanClientPayeur est invalide"}. The split above
     * satisfies both.
     */
    private static void putAccountId(Map<String, Object> payload,
                                     String ibanKey, String othrKey,
                                     TypeCompte typeCompte,
                                     String iban, String other) {
        String value;
        String key;
        if (typeCompte == TypeCompte.TRAL) {
            key = othrKey;
            value = other != null ? other : iban;
        } else if (typeCompte == TypeCompte.CACC
                || typeCompte == TypeCompte.SVGS
                || typeCompte == TypeCompte.LLSV
                || typeCompte == TypeCompte.VACC
                || typeCompte == TypeCompte.TAXE) {
            // Strict IBAN types — schema requires <IBAN>
            key = ibanKey;
            value = iban != null ? iban : other;
        } else {
            // TRAN or null — prefer iban if present, else other
            if (iban != null) { key = ibanKey; value = iban; }
            else if (other != null) { key = othrKey; value = other; }
            else return;
        }
        if (value != null) payload.put(key, value);
    }
}
