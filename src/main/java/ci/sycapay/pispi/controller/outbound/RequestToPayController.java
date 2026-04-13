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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Request to Pay")
@RestController
@RequestMapping("/api/v1/rtp")
@RequiredArgsConstructor
public class RequestToPayController {

    private final RequestToPayService rtpService;

    @Operation(summary = "Create a Request-to-Pay",
               description = "Sends a PAIN.013 Request-to-Pay to the AIP targeting the payee participant. The payee PI will receive it via callback and may accept or reject.")
    @PostMapping
    public ResponseEntity<ApiResponse<RequestToPayResponse>> createRtp(
            @Valid @RequestBody RequestToPayRequest request) {
        RequestToPayResponse data = rtpService.createRtp(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Get a Request-to-Pay by endToEndId", description = "Retrieves the local state of a Request-to-Pay message.")
    @GetMapping("/{endToEndId}")
    public ApiResponse<RequestToPayResponse> getRtp(@Parameter(description = "End-to-end identifier of the RTP") @PathVariable String endToEndId) {
        return ApiResponse.ok(rtpService.getRtp(endToEndId));
    }

    @Operation(summary = "List outbound Request-to-Pay messages", description = "Returns a paginated list of all RTPs initiated by this PI.")
    @GetMapping
    public ApiResponse<Page<RequestToPayResponse>> listRtps(Pageable pageable) {
        return ApiResponse.ok(rtpService.listRtps(pageable));
    }

    @Operation(summary = "Reject an inbound Request-to-Pay",
               description = "Sends a PAIN.014 rejection response to the AIP for an inbound RTP received via callback. Requires a rejection reason code.")
    @PostMapping("/incoming/{endToEndId}/reject")
    public ApiResponse<RequestToPayResponse> rejectRtp(
            @PathVariable String endToEndId,
            @Valid @RequestBody RtpRejectRequest request) {
        return ApiResponse.ok(rtpService.rejectRtp(endToEndId, request.getCodeRaison()));
    }

    @Operation(summary = "Accept an inbound Request-to-Pay",
               description = "Acknowledges acceptance of an inbound RTP. The subsequent credit transfer (PACS.008) should be initiated separately via POST /api/v1/transfers.")
    @PostMapping("/incoming/{endToEndId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRtp(@Parameter(description = "End-to-end identifier of the inbound RTP") @PathVariable String endToEndId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }
}
