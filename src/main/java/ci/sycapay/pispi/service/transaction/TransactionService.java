package ci.sycapay.pispi.service.transaction;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnRejectRequest;
import ci.sycapay.pispi.dto.rtp.RequestToPayRequest;
import ci.sycapay.pispi.dto.rtp.RequestToPayResponse;
import ci.sycapay.pispi.dto.transaction.*;
import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.exception.InvalidStateException;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.resolver.ClientSearchResolver;
import ci.sycapay.pispi.service.resolver.ResolvedClient;
import ci.sycapay.pispi.service.returnfunds.ReturnFundsService;
import ci.sycapay.pispi.service.rtp.RequestToPayService;
import ci.sycapay.pispi.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
 * <p><b>Wired actions:</b>
 * <ul>
 *   <li>{@code send_now} with an {@code alias} beneficiary — full PACS.008
 *       flow (the iban+PSP / othr+PSP beneficiary modes still return 501
 *       pending the inline RAC_SEARCH work).</li>
 *   <li>{@code send_schedule} — Programme + Abonnement: persists the recipe
 *       and is picked up by {@link TransactionScheduleRunner} to spawn child
 *       PACS.008 emissions.</li>
 *   <li>{@code receive_now} — RTP: delegates to {@link RequestToPayService}
 *       which POSTs PAIN.013 to {@code /demandes-paiements}. Roles invert
 *       (mobile user = payé; counterparty resolved by {@code alias} = payeur).
 *       Canal is remapped from the spec's mobile-default {@code 631} to
 *       {@code 500} when the payé is type B/G/C.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final PiTransferRepository transferRepository;
    private final PiRequestToPayRepository rtpRepository;
    private final PiReturnRequestRepository returnRequestRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;
    private final ClientSearchResolver clientSearchResolver;
    private final ReturnFundsService returnFundsService;
    private final RequestToPayService requestToPayService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------------
    // Initiation
    // ------------------------------------------------------------------------

    @Transactional
    public TransactionResponse initiate(TransactionInitiationRequest request) {
        return switch (request.getAction()) {
            case SEND_NOW -> initiateSendNow((TransactionImmediatRequest) request);
            case SEND_SCHEDULE -> initiateSendSchedule((TransactionScheduleRequest) request);
            case RECEIVE_NOW -> initiateReceiveNow(
                    (ci.sycapay.pispi.dto.transaction.TransactionDemandePaiementRequest) request);
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
        // BCEAO spec : "Le EndToEndId envoyée lors de la recherche d'alias ou
        // de la vérification d'identité (Id de la vérification) est le même
        // que celui du transfert." Nous sommes le payeur (OUTBOUND PACS.008) ;
        // la RAC_SEARCH que nous avons faite sur le payé porte l'e2e exposé
        // par le resolver — on le réutilise plutôt que d'en générer un neuf.
        String endToEndId = paye.endToEndIdSearch();
        String identifiantTransaction = resolveIdentifiantTransaction(
                canal, request.getTxId(), endToEndId);

        PiTransfer.PiTransferBuilder builder = PiTransfer.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(TypeTransaction.PRMG)
                .canalCommunication(canal)
                .montant(request.getMontant())
                .devise("XOF");
        applyPayeurSnapshot(builder, codeMembre, payeur, request.getEndToEndIdSearchPayeur());
        applyPayeSnapshot(builder, paye);
        PiTransfer transfer = builder
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

        LocalDateTime dateDebut = request.getDateDebut()
                .withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime dateFin = request.getDateFin() != null
                ? request.getDateFin().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
                : null;

        PiTransfer.PiTransferBuilder scheduleBuilder = PiTransfer.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(TypeTransaction.PRMG)
                .canalCommunication(canal)
                .montant(request.getMontant())
                .devise("XOF");
        applyPayeurSnapshot(scheduleBuilder, codeMembre, payeur, request.getEndToEndIdSearchPayeur());
        applyPayeSnapshot(scheduleBuilder, paye);
        PiTransfer schedule = scheduleBuilder
                .motif(request.getMotif())
                .latitudeClientPayeur(request.getLatitude())
                .longitudeClientPayeur(request.getLongitude())
                .dateHeureExecution(LocalDateTime.now())
                .action(TransactionAction.SEND_SCHEDULE)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .frequence(request.getFrequence())
                .periodicite(request.getPeriodicite())
                .nextExecutionDate(dateDebut.toLocalDate())
                .active(Boolean.TRUE)
                .subscriptionId(endToEndId)
                .statut(TransferStatus.INITIE)
                .build();
        transferRepository.save(schedule);

        log.info("Schedule ({}) persisted [endToEndId={}, dateDebut={}, frequence={}, periodicite={}]",
                request.isSubscription() ? "Abonnement" : "Programme",
                endToEndId, dateDebut, request.getFrequence(), request.getPeriodicite());
        return toResponse(schedule);
    }

    // ------------------------------------------------------------------------
    // Initiation — receive_now (RTP / demande de paiement)
    // ------------------------------------------------------------------------

    /**
     * Initiate a Request-to-Pay: our client (the payé) asks a counterparty
     * to pay. Emits a PAIN.013 to the AIP via the legacy RTP flow on
     * {@code /demandes-paiements}.
     *
     * <p>Role mapping in this flow vs. {@code send_now}:
     * <ul>
     *   <li>Mobile user = <b>payé</b> (creditor — the one requesting money);
     *       resolved from the {@code endToEndIdSearchPayeur} DTO bridge field
     *       (legacy name from send_now — semantically it carries "the
     *       initiator's own RAC_SEARCH" for both flows). Must be a
     *       {@code properties.codeMembre} client (validated below).</li>
     *   <li>Counterparty = <b>payeur</b> (debtor — the one being asked);
     *       resolved from the {@code alias} field. Can be at any participant.</li>
     * </ul>
     *
     * <p>Canal remap per BCEAO PI-RAC operational rules (and the remote
     * spec's note "lorsque le canal est 631 alors le participant doit dans
     * le backend le remplacer par 500 si son client est un Commerçant
     * Type C"): mobile sends {@code 631}, but if the payé is B/G/C we
     * promote to {@code 500} (marchand sur site).
     */
    @Transactional
    public TransactionResponse initiateReceiveNow(
            ci.sycapay.pispi.dto.transaction.TransactionDemandePaiementRequest request) {

        // Resolve the requester (our client = paye) and the counterparty
        // (the person being asked = payeur).
        ResolvedClient paye = clientSearchResolver.resolve(
                request.getEndToEndIdSearchPayeur(), "paye");
        ResolvedClient payeur = clientSearchResolver.resolveByAlias(request.getAlias(), "payeur");

        // The requester (paye) must be one of OUR clients — a payment cannot
        // legitimately be initiated from outside our participant. Mirror of
        // validatePayeurOwnership for the inverted role.
        validatePayeOwnership(paye);

        // Canal remap: P payé stays on 631 (particulier), B/G/C bumps to 500.
        CanalCommunicationRtp rtpCanal =
                remapReceiveNowCanal(paye.clientInfo().getTypeClient());

        String codeMembre = properties.getCodeMembre();
        // identifiantDemandePaiement is a free-form unique id; reusing the
        // msgId generator gives us a 35-char unique value with the right
        // shape (DPxxxxx... stays in line with the M/E prefixes on the
        // other identifiers).
        String identifiantDemande = IdGenerator.generateMsgId(codeMembre);

        // The legacy RTP DTO takes endToEndIdSearchPayeur as the COUNTERPARTY's
        // endToEndId; we look it up from the alias the mobile sent.
        String endToEndIdSearchPayeur = clientSearchResolver
                .findEndToEndIdByAlias(request.getAlias());

        RequestToPayRequest rtpReq =
                RequestToPayRequest.builder()
                        .canalCommunication(rtpCanal)
                        .clientDemandeur("X") // BCEAO InitgPty>Nm: "X" = the payé client
                        .identifiantDemandePaiement(identifiantDemande)
                        .montant(request.getMontant())
                        .endToEndIdSearchPayeur(endToEndIdSearchPayeur)
                        .endToEndIdSearchPaye(request.getEndToEndIdSearchPayeur())
                        .latitudeClientPaye(request.getLatitude())
                        .longitudeClientPaye(request.getLongitude())
                        .motif(request.getMotif())
                        .build();

        RequestToPayResponse rtpResp =
                requestToPayService.createRtp(rtpReq);

        log.info("RTP PAIN.013 émis depuis le flux mobile [endToEndId={}, payé={}, payeur={}, canal={}]",
                rtpResp.getEndToEndId(), codeMembre, payeur.codeMembre(), rtpCanal);

        return toResponseFromRtp(rtpResp, paye, payeur, rtpCanal, request);
    }

    /** Choose the PAIN.013 canal based on the payé's typeClient. */
    private CanalCommunicationRtp remapReceiveNowCanal(
            TypeClient payeType) {
        if (payeType == null) {
            return CanalCommunicationRtp.PARTICULIER; // safe default
        }
        return switch (payeType) {
            case B, G, C -> CanalCommunicationRtp.MARCHAND_SUR_SITE; // 500
            case P -> CanalCommunicationRtp.PARTICULIER;             // 631
        };
    }

    /**
     * Refuse to initiate a receive_now whose payé (requester) doesn't belong
     * to our participant.
     */
    private void validatePayeOwnership(ResolvedClient paye) {
        String ours = properties.getCodeMembre();
        String theirs = paye.codeMembre();
        if (theirs != null && !theirs.equals(ours)) {
            throw new InvalidStateException(
                    "Le payé résolu (le demandeur) appartient au participant " + theirs
                            + " mais nous sommes " + ours + ". "
                            + "Une demande de paiement ne peut être émise que par un de nos "
                            + "clients — vérifiez que 'endToEndIdSearchPayeur' pointe sur la "
                            + "RAC_SEARCH de votre propre client (le demandeur).");
        }
    }

    /** Map the legacy {@code RequestToPayResponse} into the unified mobile shape. */
    private TransactionResponse toResponseFromRtp(
            RequestToPayResponse rtp,
            ResolvedClient paye,
            ResolvedClient payeur,
            CanalCommunicationRtp rtpCanal,
            TransactionDemandePaiementRequest request) {
        return TransactionResponse.builder()
                .compte(paye.other() != null ? paye.other() : paye.iban())
                .alias(paye.aliasValue())
                .canal(rtpCanal.getCode())
                .montant(rtp.getMontant() != null ? rtp.getMontant() : request.getMontant())
                .endToEndId(rtp.getEndToEndId())
                // For receive_now the mobile user is the creditor; "sens" from
                // the requester's view is therefore "credit" (incoming money
                // is what they expect once the payeur accepts).
                .sens("credit")
                .motif(request.getMotif())
                // Counterparty snapshot — the person being asked
                .clientNom(payeur.clientInfo().getNom())
                .clientPays(payeur.clientInfo().getPays())
                .clientPSP(payeur.codeMembre())
                .clientCompte(payeur.iban() != null ? payeur.iban() : payeur.other())
                .clientAlias(payeur.aliasValue())
                .statut(TransactionStatut.INITIE)
                .dateDemande(LocalDateTime.now().atOffset(ZoneOffset.UTC))
                .build();
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
        // TODO(spec §4.2 + spec PACS.008 e2e=RAC_SEARCH e2e) : chaque exécution
        // d'un planning devrait re-déclencher une RAC_SEARCH sur l'alias du
        // payé (pas de cache autorisé) et utiliser SON e2e ici. Tant que la
        // RAC_SEARCH n'est pas relancée par le scheduler, on génère un e2e
        // neuf — le PACS.008 résultant n'aura pas de RAC_SEARCH parente
        // traceable côté AIP. À corriger en intégrant un appel
        // {@link ClientSearchResolver#resolveByAlias} ici (qui re-déclenche
        // une RAC_SEARCH si rien de récent n'est en log).
        String childEndToEndId = IdGenerator.generateEndToEndId(codeMembre);
        // Each execution is its own transaction — generate a fresh identifiant-
        // Transaction per child (the parent recipe never had one, by design).
        String childTxId = resolveIdentifiantTransaction(
                parent.getCanalCommunication(), null, childEndToEndId);

        PiTransfer.PiTransferBuilder childBuilder = PiTransfer.builder()
                .msgId(childMsgId)
                .endToEndId(childEndToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(parent.getTypeTransaction())
                .canalCommunication(parent.getCanalCommunication())
                .montant(parent.getMontant())
                .devise(parent.getDevise());
        copyPayeurFromParent(childBuilder, parent);
        copyPayeFromParent(childBuilder, parent);
        PiTransfer child = childBuilder
                .motif(parent.getMotif())
                .identifiantTransaction(childTxId)
                .latitudeClientPayeur(parent.getLatitudeClientPayeur())
                .longitudeClientPayeur(parent.getLongitudeClientPayeur())
                .dateHeureExecution(LocalDateTime.now())
                .statut(TransferStatus.PEND)
                .action(TransactionAction.SEND_NOW)
                .parentScheduleId(parent.getId())
                .subscriptionId(parent.getSubscriptionId())
                .confirmationDate(LocalDateTime.now())
                .build();
        transferRepository.save(child);

        emitPacs008(child);

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
        Optional<PiTransfer> transferOpt = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND);
        if (transferOpt.isPresent()) {
            return confirmTransfer(transferOpt.get(), cmd);
        }
        // No outbound transfer — check for an RTP (BCEAO PUT /transferts/{id} covers
        // "Confirmer transfert", "Accepter RTP entrant" AND "Accepter RTP sortant
        // sens crédit": when we initiated the PAIN.013 as creditor (direction=OUTBOUND)
        // the debtor confirms acceptance here, triggering the PACS.008 payment to us).
        // Direction-agnostic lookup — covers both branches (INBOUND for "accept
        // an inbound RTP" and OUTBOUND for "accept the credit-side of an RTP we
        // initiated"). The composite unique (V42) means both legs may exist
        // when the deux participants sont sur cette plateforme.
        PiRequestToPay rtp = rtpRepository.findFirstByEndToEndIdOrderByIdDesc(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", endToEndId));
        return confirmRtpAcceptance(rtp, cmd);
    }

    private TransactionResponse confirmTransfer(PiTransfer t, TransactionConfirmCommand cmd) {
        if (t.getStatut() != TransferStatus.INITIE) {
            throw new InvalidStateException(
                    "La transaction " + t.getEndToEndId() + " est en statut " + t.getStatut()
                            + " — seules les transactions INITIE peuvent être confirmées");
        }
        if (cmd.getMontant().compareTo(t.getMontant()) != 0) {
            throw new InvalidStateException(
                    "Le montant de confirmation (" + cmd.getMontant() + ") ne correspond pas "
                            + "au montant initié (" + t.getMontant() + ")");
        }

        emitPacs008(t);

        t.setStatut(TransferStatus.PEND);
        t.setConfirmationDate(cmd.getConfirmationDate().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        t.setConfirmationMethode(cmd.getConfirmationMethode());
        if (cmd.getLatitude() != null)  t.setLatitudeClientPayeur(cmd.getLatitude().toPlainString());
        if (cmd.getLongitude() != null) t.setLongitudeClientPayeur(cmd.getLongitude().toPlainString());
        if (cmd.getMotif() != null && !cmd.getMotif().isBlank()) t.setMotif(cmd.getMotif());
        transferRepository.save(t);

        log.info("Transaction {} confirmée et PACS.008 émis [method={}]",
                t.getEndToEndId(), cmd.getConfirmationMethode());
        return toResponse(t);
    }

    /**
     * Accept an inbound RTP via the BCEAO {@code PUT /transferts/{id}} flow.
     * Builds and emits a PACS.008 from the RTP snapshot, creates an outbound
     * {@link PiTransfer} row (PEND), and marks the RTP as ACCEPTED with a back-
     * reference to the new transfer's {@code endToEndId}.
     */
    private TransactionResponse confirmRtpAcceptance(PiRequestToPay rtp, TransactionConfirmCommand cmd) {
        // BCEAO rule: PACS.008 montant = PAIN.013 montant − discount.
        // Discount is either a fixed amount (montantRemisePaiementImmediat) or a percentage
        // rate (remiseRate / tauxRemisePaiementImmediat). Amount takes precedence over rate.
        // montantAchat stays GROSS; line-sum montantAchat + montantRetrait = PAIN.013 montant.
        BigDecimal effectiveMontant = computeEffectiveMontant(rtp, cmd.getMontant());

        if (rtp.getMontant() != null && cmd.getMontant().compareTo(rtp.getMontant()) != 0) {
            throw new InvalidStateException(
                    "Le montant de confirmation (" + cmd.getMontant() + ") ne correspond pas "
                            + "au montant de la demande de paiement (" + rtp.getMontant() + ")");
        }

        String codeMembre = properties.getCodeMembre();
        String newMsgId = IdGenerator.generateMsgId(codeMembre);

        CanalCommunication canal = rtp.getCanalCommunication() != null
                ? CanalCommunication.fromCode(rtp.getCanalCommunication().getCode())
                : null;

        // BCEAO : « La localisation du client payeur est obligatoire pour les
        // canaux '731', '633', '000', '400', '500', '521', '520', '631' et
        // '401'. » Le payeur (= nous, le débiteur qui accepte le RTP) doit
        // donc fournir lat/lon dans le {@code TransactionConfirmCommand} —
        // typiquement la GPS capturée au moment du PIN/biométrie côté mobile.
        // On rejette en local plutôt que d'attendre l'ADMI.002 (le retry
        // depuis PREVALIDATION coûte un round-trip AIP inutile).
        if (canal != null && CANALS_REQUIRING_LOCALISATION.contains(canal)
                && (cmd.getLatitude() == null || cmd.getLongitude() == null)) {
            throw new IllegalArgumentException(
                    "Localisation du payeur obligatoire pour le canal " + canal.name()
                            + " (" + canal.getCode() + "). Renseigner 'latitude' et "
                            + "'longitude' dans le corps de l'acceptation (capture GPS "
                            + "au moment du PIN/biométrie côté mobile). Sans cela, "
                            + "l'AIP rejette le PACS.008 avec ADMI.002 \"La localisation "
                            + "du client payeur est obligatoire pour les canaux ...\".");
        }

        PiTransfer transfer = PiTransfer.builder()
                .msgId(newMsgId)
                .endToEndId(rtp.getEndToEndId())
                // Lien explicite Transfer→RTP (V44) pour que le callback PACS.002
                // retrouve le RTP parent via findByRtpEndToEndIdAndDirection
                // sans dépendre d'un timing implicite (findFirst + filtre PREVALIDATION).
                .rtpEndToEndId(rtp.getEndToEndId())
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(TypeTransaction.PRMG)
                .canalCommunication(canal)
                .montant(effectiveMontant)
                .devise("XOF")
                // Payeur (this PI — the debtor)
                .codeMembrePayeur(rtp.getCodeMembrePayeur() != null ? rtp.getCodeMembrePayeur() : codeMembre)
                .ibanClientPayeur(rtp.getIbanClientPayeur())
                .numeroComptePayeur(rtp.getOtherClientPayeur())
                .typeComptePayeur(rtp.getTypeComptePayeur())
                .nomClientPayeur(rtp.getNomClientPayeur())
                .typeClientPayeur(rtp.getTypeClientPayeur())
                .telephonePayeur(rtp.getTelephonePayeur())
                .paysClientPayeur(rtp.getPaysClientPayeur())
                .villeClientPayeur(rtp.getVilleClientPayeur())
                .aliasPayeur(rtp.getAliasClientPayeur())
                .identifiantClientPayeur(rtp.getNumeroIdentificationPayeur())
                .typeIdentifiantClientPayeur(rtp.getSystemeIdentificationPayeur())
                .identificationFiscaleCommercantPayeur(rtp.getIdentificationFiscalePayeur())
                .identificationRccmClientPayeur(rtp.getNumeroRCCMPayeur())
                // Payé (the requesting party — the creditor)
                .codeMembrePaye(rtp.getCodeMembrePaye())
                .ibanClientPaye(rtp.getIbanClientPaye())
                .numeroComptePaye(rtp.getOtherClientPaye())
                .typeComptePaye(rtp.getTypeComptePaye())
                .nomClientPaye(rtp.getNomClientPaye())
                .typeClientPaye(rtp.getTypeClientPaye())
                .telephonePaye(rtp.getTelephonePaye())
                .paysClientPaye(rtp.getPaysClientPaye())
                .villeClientPaye(rtp.getVilleClientPaye())
                .aliasPaye(rtp.getAliasClientPaye())
                .identifiantClientPaye(rtp.getNumeroIdentificationPaye())
                .typeIdentifiantClientPaye(rtp.getSystemeIdentificationPaye())
                .identificationFiscaleCommercantPaye(rtp.getIdentificationFiscalePaye())
                .identificationRccmClientPaye(rtp.getNumeroRCCMPaye())
                // Transaction details
                .motif(cmd.getMotif() != null && !cmd.getMotif().isBlank()
                        ? cmd.getMotif() : rtp.getMotif())
                .identifiantTransaction(rtp.getIdentifiantDemandePaiement())
                .latitudeClientPayeur(cmd.getLatitude() != null ? cmd.getLatitude().toPlainString() : null)
                .longitudeClientPayeur(cmd.getLongitude() != null ? cmd.getLongitude().toPlainString() : null)
                .dateHeureExecution(LocalDateTime.now())
                .confirmationDate(cmd.getConfirmationDate().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .confirmationMethode(cmd.getConfirmationMethode())
                .statut(TransferStatus.PEND)
                .build();
        transferRepository.save(transfer);

        emitPacs008(transfer, buildRtpExtra(rtp));

        rtp.setStatut(RtpStatus.PREVALIDATION);
        rtp.setTransferEndToEndId(rtp.getEndToEndId());
        rtpRepository.save(rtp);

        log.info("RTP PACS.008 émis, passage en PREVALIDATION [rtpEndToEndId={}, method={}]",
                rtp.getEndToEndId(), cmd.getConfirmationMethode());
        return toResponse(transfer);
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
        log.debug("CAMT.056 payload: {}", camt056);
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
    // Inbound RTP — accept (émet PACS.008 d'acceptation)
    // ------------------------------------------------------------------------

    /**
     * Accepter une demande de paiement <strong>entrante</strong> (PAIN.013
     * reçu en INBOUND). Nous sommes le débiteur ; on émet immédiatement le
     * PACS.008 vers l'AIP et la ligne RTP locale passe en PREVALIDATION.
     *
     * <p>Variante direction-aware de {@link #confirm(String, TransactionConfirmCommand)}
     * — utilisée par {@code POST /api/v1/rtp/incoming/{e2e}/accept} pour lever
     * l'ambiguïté en multi-tenant : avec la contrainte composite V42, deux
     * lignes peuvent partager le même {@code endToEndId} (l'OUTBOUND du PI
     * créditeur et l'INBOUND du PI débiteur, tous deux gérés par cette
     * plateforme). Le {@code findFirst...OrderByIdDesc} générique de
     * {@link #confirm} retournerait n'importe laquelle des deux ; ici on
     * épingle explicitement la ligne INBOUND, qui est la seule sémantiquement
     * correcte pour l'opération « accepter une demande reçue ».
     */
    @Transactional
    public TransactionResponse acceptInboundRtp(String endToEndId, TransactionConfirmCommand cmd) {
        PiRequestToPay rtp = rtpRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
                .orElseThrow(() -> new ResourceNotFoundException("RTP entrant", endToEndId));
        if (rtp.getStatut().isTerminal()) {
            log.info("La demande de paiement a atteint une phase terminal.");
            throw new InvalidStateException("La demande de paiement a atteint une phase terminal.");
        }
        return confirmRtpAcceptance(rtp, cmd);
    }

    // ------------------------------------------------------------------------
    // Inbound PACS.008 — accept / reject (émission PACS.002)
    // ------------------------------------------------------------------------

    /**
     * Accepter un PACS.008 reçu (nous sommes le payé). Émet un PACS.002 avec
     * {@code statutTransaction=ACCC} vers {@code POST /transferts/reponses} et
     * fait avancer la ligne locale {@code pi_transfer} INBOUND de PEND à ACCC.
     *
     * <p>BCEAO §4.3 : « Un participant payé reçoit un ordre de transfert de
     * fonds (pacs.008) et doit retourner un pacs.002 qui précisera à PI le
     * traitement à faire pour ce transfert. »
     *
     * <p>Sémantique « réserver puis débiter » : à l'acceptation (ici), le
     * backend réserve les fonds chez le payé / crédite le compte (selon
     * politique). Le débit effectif côté PAYEUR n'arrivera qu'à la réception
     * du PACS.002 d'avis de débit du côté payeur — ce n'est pas notre
     * problème ici, on ne fait que confirmer la réception du transfert.
     */
    @Transactional
    public TransactionResponse acceptIncomingTransfer(String endToEndId) {
        PiTransfer transfer = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transfert entrant", endToEndId));

        if (transfer.getStatut() != TransferStatus.PEND) {
            throw new InvalidStateException(
                    "Le transfert entrant " + endToEndId + " est en statut "
                            + transfer.getStatut() + " — seules les lignes PEND "
                            + "(en attente de réponse) peuvent être acceptées.");
        }

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String dateHeureIrrevocabilite = nowIso();

        Map<String, Object> pacs002 = new HashMap<>();
        pacs002.put("msgId", msgId);
        pacs002.put("msgIdDemande", transfer.getMsgId());
        pacs002.put("endToEndId", endToEndId);
        pacs002.put("statutTransaction", TransferStatus.ACCC.name());
        pacs002.put("dateHeureIrrevocabilite", dateHeureIrrevocabilite);

        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_002,
                MessageDirection.OUTBOUND, pacs002, null, null);
        aipClient.post("/transferts/reponses", pacs002);

        transfer.setStatut(TransferStatus.ACCC);
        transfer.setMsgIdReponse(msgId);
        transfer.setDateHeureIrrevocabilite(LocalDateTime.now(ZoneOffset.UTC));
        transferRepository.save(transfer);

        log.info("PACS.002 ACCC émis pour transfert entrant [endToEndId={}]", endToEndId);
        return toResponse(transfer);
    }

    /**
     * Rejeter un PACS.008 reçu (nous sommes le payé). Émet un PACS.002 avec
     * {@code statutTransaction=RJCT} et un {@code codeRaison} conforme au
     * pattern BCEAO. Fait passer la ligne locale {@code pi_transfer} INBOUND
     * de PEND à RJCT.
     *
     * <p>Codes typiques : AC01 (compte introuvable), AC04 (compte clôturé),
     * BE01 (identité incohérente), FR01 (fraude), MS03 (raison non spécifiée).
     */
    @Transactional
    public TransactionResponse rejectIncomingTransfer(String endToEndId,
                                                     IncomingTransferRejectCommand cmd) {
        PiTransfer transfer = transferRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transfert entrant", endToEndId));

        if (transfer.getStatut() != TransferStatus.PEND) {
            throw new InvalidStateException(
                    "Le transfert entrant " + endToEndId + " est en statut "
                            + transfer.getStatut() + " — seules les lignes PEND "
                            + "peuvent être rejetées.");
        }

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> pacs002 = new HashMap<>();
        pacs002.put("msgId", msgId);
        pacs002.put("msgIdDemande", transfer.getMsgId());
        pacs002.put("endToEndId", endToEndId);
        pacs002.put("statutTransaction", TransferStatus.RJCT.name());
        pacs002.put("codeRaison", cmd.getCodeRaison());

        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_002,
                MessageDirection.OUTBOUND, pacs002, null, null);
        aipClient.post("/transferts/reponses", pacs002);

        transfer.setStatut(TransferStatus.RJCT);
        transfer.setCodeRaison(cmd.getCodeRaison());
        transfer.setMsgIdReponse(msgId);
        transferRepository.save(transfer);

        log.info("PACS.002 RJCT émis pour transfert entrant [endToEndId={}, codeRaison={}]",
                endToEndId, cmd.getCodeRaison());
        return toResponse(transfer);
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
     * <em>"TransactionIdentificatiKon est obligatoire lorsqu'il s'agit des
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
    // Snapshot builder helpers
    // ------------------------------------------------------------------------

    private static BigDecimal computeEffectiveMontant(PiRequestToPay rtp, BigDecimal fallback) {
        BigDecimal gross = rtp.getMontant() != null ? rtp.getMontant() : fallback;
        BigDecimal fixedRemise = rtp.getMontantRemisePaiementImmediat();
        BigDecimal rateRemise = rtp.getTauxRemisePaiementImmediat();
        if (fixedRemise != null && fixedRemise.signum() > 0) {
            return gross.subtract(fixedRemise);
        }
        if (rateRemise != null && rateRemise.signum() > 0) {
            BigDecimal discount = gross.multiply(rateRemise)
                    .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
            return gross.subtract(discount);
        }
        return gross;
    }

    /**
     * Extra PACS.008 fields that are present in an inbound PAIN.013 (RTP) but have
     * no equivalent column on {@link PiTransfer}: monetary breakdown and birth data.
     * The AIP validates that the accepting PACS.008 carries the same monetary
     * breakdown as the original PAIN.013 — omitting montantAchat / montantRetrait
     * causes codeRaisonRejet=TransactionNotFound.
     */
    private static Map<String, Object> buildRtpExtra(PiRequestToPay rtp) {
        Map<String, Object> extra = new HashMap<>();
        // Mirror all monetary fields from the PAIN.013 unchanged so the AIP can
        // match the PACS.008 back to the original request. The remise is passed
        // as-is: the AIP uses it to compute the net settlement itself (AM09 if absent).
        if (rtp.getMontantAchat() != null)
            extra.put("montantAchat", rtp.getMontantAchat().toBigInteger().toString());
        if (rtp.getMontantRetrait() != null)
            extra.put("montantRetrait", rtp.getMontantRetrait().toBigInteger().toString());
        if (rtp.getFraisRetrait() != null)
            extra.put("fraisRetrait", rtp.getFraisRetrait().toBigInteger().toString());
        if (rtp.getDateNaissancePayeur() != null)
            extra.put("dateNaissanceClientPayeur", rtp.getDateNaissancePayeur());
        if (rtp.getVilleNaissancePayeur() != null)
            extra.put("villeNaissanceClientPayeur", rtp.getVilleNaissancePayeur());
        if (rtp.getPaysNaissancePayeur() != null)
            extra.put("paysNaissanceClientPayeur", rtp.getPaysNaissancePayeur());
        if (rtp.getDateNaissancePaye() != null)
            extra.put("dateNaissanceClientPaye", rtp.getDateNaissancePaye());
        if (rtp.getVilleNaissancePaye() != null)
            extra.put("villeNaissanceClientPaye", rtp.getVilleNaissancePaye());
        if (rtp.getPaysNaissancePaye() != null)
            extra.put("paysNaissanceClientPaye", rtp.getPaysNaissancePaye());
        return extra;
    }

    private static void applyPayeurSnapshot(PiTransfer.PiTransferBuilder b,
                                             String codeMembre, ResolvedClient payeur,
                                             String racSearchRef) {
        b.codeMembrePayeur(codeMembre)
                .numeroComptePayeur(payeur.other())
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
                .racSearchRefPayeur(racSearchRef);
    }

    private static void applyPayeSnapshot(PiTransfer.PiTransferBuilder b, ResolvedClient paye) {
        b.codeMembrePaye(paye.codeMembre())
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
                .adresseClientPaye(paye.clientInfo().getAdresse());
    }

    private static void copyPayeurFromParent(PiTransfer.PiTransferBuilder b, PiTransfer parent) {
        b.codeMembrePayeur(parent.getCodeMembrePayeur())
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
                .racSearchRefPayeur(parent.getRacSearchRefPayeur());
    }

    private static void copyPayeFromParent(PiTransfer.PiTransferBuilder b, PiTransfer parent) {
        b.codeMembrePaye(parent.getCodeMembrePaye())
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
                .racSearchRefPaye(parent.getRacSearchRefPaye());
    }

    private void emitPacs008(PiTransfer transfer) {
        emitPacs008(transfer, null);
    }

    private void emitPacs008(PiTransfer transfer, Map<String, Object> extra) {
        Map<String, Object> pacs008 = buildPacs008FromSnapshot(transfer);
        if (extra != null) pacs008.putAll(extra);
        messageLogService.log(transfer.getMsgId(), transfer.getEndToEndId(),
                IsoMessageType.PACS_008, MessageDirection.OUTBOUND, pacs008, null, null);
        try {
            log.debug("PACS.008 payload: {}", objectMapper.writeValueAsString(pacs008));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        aipClient.post("/transferts", pacs008);
    }

    // ------------------------------------------------------------------------
    // PACS.008 payload rebuild from snapshot
    // ------------------------------------------------------------------------

    private Map<String, Object> buildPacs008FromSnapshot(PiTransfer t) {
        Map<String, Object> p = new HashMap<>();
        p.put("msgId", t.getMsgId());
        p.put("endToEndId", t.getEndToEndId());
        p.put("canalCommunication", t.getCanalCommunication().getCode());
        // Identification des deux PSP : sans {@code codeMembreParticipantPayeur}
        // sur le PACS.008, BCEAO rejette l'acceptation d'un RTP avec
        // "Les données de la demande de paiement et du transfert ne correspondent
        // pas" (TransactionNotFound) — le PAIN.013 préalable porte ce champ
        // (cf. RequestToPayService.buildPain013Payload : codeMembreParticipantPayeur),
        // l'AIP fait un cross-check sur l'endToEndId partagé.
        if (t.getCodeMembrePayeur() != null)
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
