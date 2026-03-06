package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.rtp.RequestToPayRequest;
import ci.sycapay.pispi.dto.rtp.RequestToPayResponse;
import ci.sycapay.pispi.dto.rtp.RtpRejectRequest;
import ci.sycapay.pispi.service.rtp.RequestToPayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rtp")
@RequiredArgsConstructor
public class RequestToPayController {

    private final RequestToPayService rtpService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RequestToPayResponse createRtp(@Valid @RequestBody RequestToPayRequest request) {
        return rtpService.createRtp(request);
    }

    @GetMapping("/{endToEndId}")
    public RequestToPayResponse getRtp(@PathVariable String endToEndId) {
        return rtpService.getRtp(endToEndId);
    }

    @GetMapping
    public Page<RequestToPayResponse> listRtps(Pageable pageable) {
        return rtpService.listRtps(pageable);
    }

    @PostMapping("/incoming/{endToEndId}/reject")
    public RequestToPayResponse rejectRtp(@PathVariable String endToEndId,
                                          @Valid @RequestBody RtpRejectRequest request) {
        return rtpService.rejectRtp(endToEndId, request.getCodeRaison());
    }

    @PostMapping("/incoming/{endToEndId}/accept")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void acceptRtp(@PathVariable String endToEndId) {
        // Accept = Payer initiates PACS.008 transfer, handled by TransferController
    }
}
