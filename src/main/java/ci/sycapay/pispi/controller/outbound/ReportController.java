package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.report.CompensationDto;
import ci.sycapay.pispi.dto.report.GuaranteeDto;
import ci.sycapay.pispi.dto.report.ReportRequest;
import ci.sycapay.pispi.enums.TypeRapport;
import ci.sycapay.pispi.service.report.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @PostMapping("/compensation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestCompensation(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.COMP, request);
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestTransactions(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.TRANS, request);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestInvoices(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.FACT, request);
    }

    @GetMapping("/compensation")
    public Page<CompensationDto> listCompensation(Pageable pageable) {
        return service.listCompensations(pageable);
    }

    @GetMapping("/guarantee")
    public GuaranteeDto getGuarantee() {
        return service.getLatestGuarantee();
    }
}
