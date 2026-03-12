package ci.sycapay.pispi.service.report;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.report.CompensationDto;
import ci.sycapay.pispi.dto.report.GuaranteeDto;
import ci.sycapay.pispi.dto.report.ReportRequest;
import ci.sycapay.pispi.entity.PiCompensation;
import ci.sycapay.pispi.entity.PiGuarantee;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TypeRapport;
import ci.sycapay.pispi.repository.PiCompensationRepository;
import ci.sycapay.pispi.repository.PiGuaranteeRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    public void requestReport(TypeRapport type, ReportRequest request) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> camt060 = new HashMap<>();
        camt060.put("msgId", msgId);
        camt060.put("typeRapport", type.name());
        camt060.put("dateDebutPeriode", request.getDateDebutPeriode());
        if (request.getHeureDebutPeriode() != null) camt060.put("heureDebutPeriode", request.getHeureDebutPeriode());

        messageLogService.log(msgId, null, IsoMessageType.CAMT_060, MessageDirection.OUTBOUND, camt060, null, null);
        aipClient.post("/api/spi/v{version}/rapport", camt060);
    }

    public Page<CompensationDto> listCompensations(Pageable pageable) {
        return compensationRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toCompensationDto);
    }

    public GuaranteeDto getLatestGuarantee() {
        return guaranteeRepository.findTopByOrderByCreatedAtDesc()
                .map(this::toGuaranteeDto)
                .orElse(null);
    }

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
