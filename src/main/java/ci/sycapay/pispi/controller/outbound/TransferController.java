package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.transfer.TransferAcceptRejectRequest;
import ci.sycapay.pispi.dto.transfer.TransferRequest;
import ci.sycapay.pispi.dto.transfer.TransferResponse;
import ci.sycapay.pispi.service.transfer.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transfers")
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse data = transferService.initiateTransfer(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @GetMapping("/{endToEndId}")
    public ApiResponse<TransferResponse> getTransfer(@PathVariable String endToEndId) {
        return ApiResponse.ok(transferService.getTransfer(endToEndId));
    }

    @GetMapping
    public ApiResponse<Page<TransferResponse>> listTransfers(Pageable pageable) {
        return ApiResponse.ok(transferService.listTransfers(pageable));
    }

    @PostMapping("/{endToEndId}/status")
    public ResponseEntity<ApiResponse<Void>> queryStatus(@PathVariable String endToEndId) {
        transferService.queryStatus(endToEndId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @PostMapping("/incoming/{endToEndId}/accept")
    public ApiResponse<TransferResponse> acceptIncoming(@PathVariable String endToEndId) {
        TransferAcceptRejectRequest req = TransferAcceptRejectRequest.builder()
                .statutTransaction(ci.sycapay.pispi.enums.StatutTransaction.ACCC)
                .build();
        return ApiResponse.ok(transferService.acceptOrReject(endToEndId, req));
    }

    @PostMapping("/incoming/{endToEndId}/reject")
    public ApiResponse<TransferResponse> rejectIncoming(@PathVariable String endToEndId,
                                                         @Valid @RequestBody TransferAcceptRejectRequest request) {
        return ApiResponse.ok(transferService.acceptOrReject(endToEndId, request));
    }
}
