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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reports")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @Operation(summary = "Request compensation report",
               description = "Sends a CAMT.060 COMP request to the AIP to retrieve the clearing/settlement compensation balances. The AIP delivers the report asynchronously via callback /api/pi/callback/compensation.")
    @PostMapping("/compensation")
    public ResponseEntity<ApiResponse<Void>> requestCompensation(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.COMP, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @Operation(summary = "Request transaction statement",
               description = "Sends a CAMT.060 TRANS request to the AIP to retrieve the transaction statement for the given period. The AIP delivers the report asynchronously via callback /api/pi/callback/releve.")
    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<Void>> requestTransactions(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.TRANS, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @Operation(summary = "Request invoice report",
               description = "Sends a CAMT.060 FACT request to the AIP to retrieve the billing invoice for the given period. The AIP delivers the report asynchronously via callback /api/pi/callback/facture.")
    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<Void>> requestInvoices(@Valid @RequestBody ReportRequest request) {
        service.requestReport(TypeRapport.FACT, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @Operation(summary = "List compensation records", description = "Returns a paginated list of all compensation balance records received from the AIP, ordered by creation date descending.")
    @GetMapping("/compensation")
    public ApiResponse<Page<CompensationDto>> listCompensation(Pageable pageable) {
        return ApiResponse.ok(service.listCompensations(pageable));
    }

    @Operation(summary = "Get latest guarantee status", description = "Returns the most recently received guarantee update (CAMT.010 or REDA.017), reflecting the current collateral position of this participant.")
    @GetMapping("/guarantee")
    public ApiResponse<GuaranteeDto> getGuarantee() {
        return ApiResponse.ok(service.getLatestGuarantee());
    }
}
