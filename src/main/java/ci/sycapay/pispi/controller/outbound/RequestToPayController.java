package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.rtp.RequestToPayRequest;
import ci.sycapay.pispi.dto.rtp.RequestToPayResponse;
import ci.sycapay.pispi.dto.rtp.RtpRejectRequest;
import ci.sycapay.pispi.service.rtp.RequestToPayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Request to Pay")
@RestController
@RequestMapping("/api/v1/rtp")
@RequiredArgsConstructor
public class RequestToPayController {

    private final RequestToPayService rtpService;

    @PostMapping
    public ResponseEntity<ApiResponse<RequestToPayResponse>> createRtp(
            @Valid @RequestBody RequestToPayRequest request) {
        RequestToPayResponse data = rtpService.createRtp(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @GetMapping("/{endToEndId}")
    public ApiResponse<RequestToPayResponse> getRtp(@PathVariable String endToEndId) {
        return ApiResponse.ok(rtpService.getRtp(endToEndId));
    }

    @GetMapping
    public ApiResponse<Page<RequestToPayResponse>> listRtps(Pageable pageable) {
        return ApiResponse.ok(rtpService.listRtps(pageable));
    }

    @PostMapping("/incoming/{endToEndId}/reject")
    public ApiResponse<RequestToPayResponse> rejectRtp(
            @PathVariable String endToEndId,
            @Valid @RequestBody RtpRejectRequest request) {
        return ApiResponse.ok(rtpService.rejectRtp(endToEndId, request.getCodeRaison()));
    }

    @PostMapping("/incoming/{endToEndId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRtp(@PathVariable String endToEndId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }
}
