package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @PostMapping("/compensation")
    public ResponseEntity<ApiResponse<Void>> requestCompensation(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.COMP, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<Void>> requestTransactions(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.TRANS, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<Void>> requestInvoices(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.FACT, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @GetMapping("/compensation")
    public ApiResponse<Page<CompensationDto>> listCompensation(Pageable pageable) {
        return ApiResponse.ok(service.listCompensations(pageable));
    }

    @GetMapping("/guarantee")
    public ApiResponse<GuaranteeDto> getGuarantee() {
        return ApiResponse.ok(service.getLatestGuarantee());
    }
}
