package ci.sycapay.pispi.service.report;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.report.CompensationDto;
import ci.sycapay.pispi.dto.report.GuaranteeDto;
import ci.sycapay.pispi.dto.report.InvoiceDto;
import ci.sycapay.pispi.dto.report.ReportRequest;
import ci.sycapay.pispi.dto.report.ReportRequestResponse;
import ci.sycapay.pispi.dto.report.TransactionReportDto;
import ci.sycapay.pispi.entity.PiCompensation;
import ci.sycapay.pispi.entity.PiGuarantee;
import ci.sycapay.pispi.entity.PiInvoice;
import ci.sycapay.pispi.entity.PiTransactionReport;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TypeRapport;
import ci.sycapay.pispi.repository.PiCompensationRepository;
import ci.sycapay.pispi.repository.PiGuaranteeRepository;
import ci.sycapay.pispi.repository.PiInvoiceRepository;
import ci.sycapay.pispi.repository.PiTransactionReportRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.formatDate;
import static ci.sycapay.pispi.util.DateTimeUtil.formatDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;
    private final PiCompensationRepository compensationRepository;
    private final PiGuaranteeRepository guaranteeRepository;
    private final PiTransactionReportRepository transactionReportRepository;
    private final PiInvoiceRepository invoiceRepository;

    /**
     * Émet un CAMT.060 (BCEAO §4.10) vers {@code /rapports/demandes} pour
     * demander un solde de compensation, un relevé de transactions, ou la
     * facture mensuelle. Le résultat arrive en async :
     *
     * <ul>
     *   <li>{@code COMP} → CAMT.053 push direct sur {@code /reglements/soldes}</li>
     *   <li>{@code TRANS} → ADMI.004 INFO avec {@code RECO...} → auto-download
     *       déclenché → CAMT.052 sur {@code /rapports/telechargements/reponses}</li>
     *   <li>{@code FACT} → CAMT.086 sur {@code /rapports/factures/reponses}</li>
     * </ul>
     *
     * <p>Retourne le {@code msgId} émis pour permettre au backend de tracer
     * la demande et corréler les callbacks.
     */
    public ReportRequestResponse requestReport(TypeRapport type, ReportRequest request) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        // BCEAO §4.10 — schéma DemandeRapport : msgId, typeRapport,
        // dateDebutPeriode (YYYY-MM-DD), heureDebutPeriode (HH:mm:ss.SSSZ).
        // La validation pattern est faite côté DTO ReportRequest.
        Map<String, Object> camt060 = new HashMap<>();
        camt060.put("msgId", msgId);
        camt060.put("typeRapport", type.name());
        camt060.put("dateDebutPeriode", request.getDateDebutPeriode());
        camt060.put("heureDebutPeriode", request.getHeureDebutPeriode());

        messageLogService.log(msgId, null, IsoMessageType.CAMT_060,
                MessageDirection.OUTBOUND, camt060, null, null);
        aipClient.post("/rapports/demandes", camt060);

        log.info("CAMT.060 émis [type={}, msgId={}, période={} {}]",
                type, msgId, request.getDateDebutPeriode(), request.getHeureDebutPeriode());

        return ReportRequestResponse.builder()
                .msgId(msgId)
                .typeRapport(type)
                .dateDebutPeriode(request.getDateDebutPeriode())
                .heureDebutPeriode(request.getHeureDebutPeriode())
                .build();
    }

    /**
     * Émet le téléchargement d'un rapport identifié par son ID BCEAO
     * ({@code RECO + codeMembre + dateCompens}). Cf. §4.11.1.1.
     *
     * <p>Normalement déclenché automatiquement par
     * {@code NotificationCallbackController.receiveNotification} quand un
     * ADMI.004 INFO arrive avec {@code evenementDescription=RECO...}.
     * L'endpoint manuel reste utile pour :
     * <ul>
     *   <li>Re-télécharger un rapport quand l'auto-trigger initial a échoué</li>
     *   <li>Récupérer un rapport ancien dont l'ADMI.004 a été perdu</li>
     * </ul>
     *
     * <p>La trace de la demande est posée dans {@code pi_message_log} (le
     * download n'a pas de msgId BCEAO — on utilise l'ID rapport comme
     * identifiant logique pour la corrélation).
     */
    public Map<String, Object> downloadReport(String reportId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", reportId);

        // Trace d'audit : on logge dans pi_message_log avec endToEndId=reportId
        // pour pouvoir corréler la demande de download au CAMT.052 qui
        // arrivera en INBOUND avec identifiantReleve=reportId.
        messageLogService.log(null, reportId, IsoMessageType.CAMT_060,
                MessageDirection.OUTBOUND, payload, null, null);

        log.info("Demande de téléchargement émise [reportId={}]", reportId);
        return aipClient.post("/rapports/telechargements", payload);
    }

    // -----------------------------------------------------------------------
    // Listing — accès paginé aux entités persistées par les callbacks AIP
    // -----------------------------------------------------------------------

    public Page<CompensationDto> listCompensations(Pageable pageable) {
        return compensationRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toCompensationDto);
    }

    public Page<TransactionReportDto> listTransactionReports(Pageable pageable) {
        return transactionReportRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toTransactionReportDto);
    }

    /** Pages d'un relevé multi-pages identifié par son {@code identifiantReleve}. */
    public Page<TransactionReportDto> listTransactionReportPages(String identifiantReleve, Pageable pageable) {
        return transactionReportRepository
                .findByIdentifiantReleveOrderByPageCouranteAsc(identifiantReleve, pageable)
                .map(this::toTransactionReportDto);
    }

    public Page<InvoiceDto> listInvoices(Pageable pageable) {
        return invoiceRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toInvoiceDto);
    }

    public GuaranteeDto getLatestGuarantee() {
        return guaranteeRepository.findTopByOrderByCreatedAtDesc()
                .map(this::toGuaranteeDto)
                .orElse(null);
    }

    // -----------------------------------------------------------------------
    // Mappers entité → DTO
    // -----------------------------------------------------------------------

    private CompensationDto toCompensationDto(PiCompensation c) {
        return CompensationDto.builder()
                .soldeId(c.getSoldeId())
                .dateDebutCompense(formatDateTime(c.getDateDebutCompense()))
                .dateFinCompense(formatDateTime(c.getDateFinCompense()))
                .participant(c.getParticipant())
                .participantSponsor(c.getParticipantSponsor())
                .balanceType(c.getBalanceType())
                .montant(c.getMontant())
                .operationType(c.getOperationType())
                .dateBalance(formatDateTime(c.getDateBalance()))
                .build();
    }

    private TransactionReportDto toTransactionReportDto(PiTransactionReport r) {
        return TransactionReportDto.builder()
                .msgId(r.getMsgId())
                .identifiantReleve(r.getIdentifiantReleve())
                .pageCourante(r.getPageCourante())
                .dernierePage(r.getDernierePage())
                .dateDebutCompense(formatDateTime(r.getDateDebutCompense()))
                .dateFinCompense(formatDateTime(r.getDateFinCompense()))
                .codeMembreParticipant(r.getCodeMembreParticipant())
                .nbreTotalTransaction(r.getNbreTotalTransaction())
                .montantTotalCompensation(r.getMontantTotalCompensation())
                .indicateurSolde(r.getIndicateurSolde())
                .transactions(r.getTransactions())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)
                .build();
    }

    private InvoiceDto toInvoiceDto(PiInvoice i) {
        return InvoiceDto.builder()
                .msgId(i.getMsgId())
                .groupeId(i.getGroupeId())
                .statementId(i.getStatementId())
                .senderName(i.getSenderName())
                .senderId(i.getSenderId())
                .receiverName(i.getReceiverName())
                .receiverId(i.getReceiverId())
                .dateDebutFacture(formatDate(i.getDateDebutFacture()))
                .dateFinFacture(formatDate(i.getDateFinFacture()))
                .deviseCompte(i.getDeviseCompte())
                .serviceLines(i.getServiceLines())
                .createdAt(i.getCreatedAt() != null ? i.getCreatedAt().toString() : null)
                .build();
    }

    private GuaranteeDto toGuaranteeDto(PiGuarantee g) {
        return GuaranteeDto.builder()
                .msgId(g.getMsgId())
                .sourceMessageType(g.getSourceMessageType())
                .participantSponsor(g.getParticipantSponsor())
                .montantGarantie(g.getMontantGarantie())
                .montantRestantGarantie(g.getMontantRestantGarantie())
                .typeOperationGarantie(g.getTypeOperationGarantie())
                .dateEffectiveGarantie(formatDateTime(g.getDateEffectiveGarantie()))
                .montantGarantiePlafond(g.getMontantGarantiePlafond())
                .dateDebut(formatDate(g.getDateDebut()))
                .dateFin(formatDate(g.getDateFin()))
                .createdAt(g.getCreatedAt() != null ? g.getCreatedAt().toString() : null)
                .build();
    }
}
