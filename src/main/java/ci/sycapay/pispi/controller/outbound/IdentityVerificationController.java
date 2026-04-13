package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationRespondRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationResponse;
import ci.sycapay.pispi.service.transfer.IdentityVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Identity Verification")
@RestController
@RequestMapping("/api/v1/verifications")
@RequiredArgsConstructor
public class IdentityVerificationController {

    private final IdentityVerificationService service;

    @PostMapping
    public ResponseEntity<ApiResponse<IdentityVerificationResponse>> requestVerification(
            @Valid @RequestBody IdentityVerificationRequest request) {
        IdentityVerificationResponse data = service.requestVerification(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @GetMapping("/{endToEndId}")
    public ApiResponse<IdentityVerificationResponse> getVerification(@PathVariable String endToEndId) {
        return ApiResponse.ok(service.getVerification(endToEndId));
    }

    @PostMapping("/incoming/{endToEndId}/respond")
    public ApiResponse<IdentityVerificationResponse> respond(
            @PathVariable String endToEndId,
            @Valid @RequestBody IdentityVerificationRespondRequest request) {
        return ApiResponse.ok(service.respond(endToEndId, request));
    }
}
