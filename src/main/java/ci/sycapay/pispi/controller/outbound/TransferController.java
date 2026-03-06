package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.transfer.TransferAcceptRejectRequest;
import ci.sycapay.pispi.dto.transfer.TransferRequest;
import ci.sycapay.pispi.dto.transfer.TransferResponse;
import ci.sycapay.pispi.service.transfer.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse initiateTransfer(@Valid @RequestBody TransferRequest request) {
        return transferService.initiateTransfer(request);
    }

    @GetMapping("/{endToEndId}")
    public TransferResponse getTransfer(@PathVariable String endToEndId) {
        return transferService.getTransfer(endToEndId);
    }

    @GetMapping
    public Page<TransferResponse> listTransfers(Pageable pageable) {
        return transferService.listTransfers(pageable);
    }

    @PostMapping("/{endToEndId}/status")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void queryStatus(@PathVariable String endToEndId) {
        transferService.queryStatus(endToEndId);
    }

    @PostMapping("/incoming/{endToEndId}/accept")
    public TransferResponse acceptIncoming(@PathVariable String endToEndId) {
        TransferAcceptRejectRequest req = TransferAcceptRejectRequest.builder()
                .statutTransaction(ci.sycapay.pispi.enums.StatutTransaction.ACCC)
                .build();
        return transferService.acceptOrReject(endToEndId, req);
    }

    @PostMapping("/incoming/{endToEndId}/reject")
    public TransferResponse rejectIncoming(@PathVariable String endToEndId,
                                           @Valid @RequestBody TransferAcceptRejectRequest request) {
        return transferService.acceptOrReject(endToEndId, request);
    }
}
