package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.transfer.TransferAcceptRejectRequest;
import ci.sycapay.pispi.dto.transfer.TransferRequest;
import ci.sycapay.pispi.dto.transfer.TransferResponse;
import ci.sycapay.pispi.service.transfer.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transfers")
@Slf4j
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Operation(summary = "Initiate a credit transfer",
               description = "Sends a PACS.008 credit transfer message to the AIP. The transfer is saved locally with status PEND and forwarded asynchronously.")
    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse data = transferService.initiateTransfer(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Get a transfer by endToEndId", description = "Retrieves the local state of a transfer using its end-to-end identifier.")
    @GetMapping("/{endToEndId}")
    public ApiResponse<TransferResponse> getTransfer(@Parameter(description = "End-to-end identifier of the transfer") @PathVariable String endToEndId) {
        return ApiResponse.ok(transferService.getTransfer(endToEndId));
    }

    @Operation(summary = "List outbound transfers", description = "Returns a paginated list of all transfers initiated by this PI, ordered by creation date descending.")
    @GetMapping
    public ApiResponse<Page<TransferResponse>> listTransfers(Pageable pageable) {
        return ApiResponse.ok(transferService.listTransfers(pageable));
    }

    @Operation(summary = "Query transfer status", description = "Sends a PACS.028 status enquiry to the AIP for the given transfer. The AIP will respond asynchronously via callback.")
    @PostMapping("/{endToEndId}/status")
    public ResponseEntity<ApiResponse<Void>> queryStatus(@Parameter(description = "End-to-end identifier of the transfer to query") @PathVariable String endToEndId) {
        transferService.queryStatus(endToEndId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @Operation(summary = "Accept an incoming transfer", description = "Sends a PACS.002 ACCC (accepted) response to the AIP for an inbound transfer received via callback.")
    @PostMapping("/incoming/{endToEndId}/accept")
    public ApiResponse<TransferResponse> acceptIncoming(@Parameter(description = "End-to-end identifier of the inbound transfer") @PathVariable String endToEndId) {
        TransferAcceptRejectRequest req = TransferAcceptRejectRequest.builder()
                .statutTransaction(ci.sycapay.pispi.enums.StatutTransaction.ACCC)
                .build();
        return ApiResponse.ok(transferService.acceptOrReject(endToEndId, req));
    }

    @Operation(summary = "Reject an incoming transfer", description = "Sends a PACS.002 RJCT (rejected) response to the AIP for an inbound transfer received via callback. Requires a rejection reason code.")
    @PostMapping("/incoming/{endToEndId}/reject")
    public ApiResponse<TransferResponse> rejectIncoming(@Parameter(description = "End-to-end identifier of the inbound transfer") @PathVariable String endToEndId,
                                                         @Valid @RequestBody TransferAcceptRejectRequest request) {
        return ApiResponse.ok(transferService.acceptOrReject(endToEndId, request));
    }
}
