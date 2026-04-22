package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationRespondRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationResponse;
import ci.sycapay.pispi.service.transfer.IdentityVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * Identity verification (ACMT.023 / ACMT.024) is a direct-participant
 * (bank / caisse) primitive per BCEAO rules — indirect participants (EMEs)
 * should resolve beneficiary identity via the RAC_SEARCH alias flow.
 *
 * <p>Hidden from OpenAPI/Swagger for indirect-participant deployments. When
 * {@code sycapay.pi-spi.code-membre} is not a direct-participant code
 * (types B/C/D/F), {@link IdentityVerificationService#requestVerification}
 * rejects the call with a 400 at the service boundary.
 */
@Hidden
@Tag(name = "Identity Verification")
@Slf4j
@RestController
@RequestMapping("/api/v1/verifications")
@RequiredArgsConstructor
public class IdentityVerificationController {

    private final IdentityVerificationService service;

    @Operation(summary = "Request account identity verification",
               description = "Sends an ACMT.023 verification request to the AIP to confirm the identity of the payee account holder. The AIP responds asynchronously via callback.")
    @PostMapping
    public ResponseEntity<ApiResponse<IdentityVerificationResponse>> requestVerification(
            @Valid @RequestBody IdentityVerificationRequest request) {
        IdentityVerificationResponse data = service.requestVerification(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Get a verification by endToEndId", description = "Retrieves the local state of an identity verification request.")
    @GetMapping("/{endToEndId}")
    public ApiResponse<IdentityVerificationResponse> getVerification(@Parameter(description = "End-to-end identifier of the verification request") @PathVariable String endToEndId) {
        return ApiResponse.ok(service.getVerification(endToEndId));
    }

    @Operation(summary = "Respond to an inbound verification request",
               description = "Sends an ACMT.024 response to an identity verification request received from the AIP via callback. Provide the verification result and an optional rejection reason code.")
    @PostMapping("/incoming/{endToEndId}/respond")
    public ApiResponse<IdentityVerificationResponse> respond(
            @PathVariable String endToEndId,
            @Valid @RequestBody IdentityVerificationRespondRequest request) {
        return ApiResponse.ok(service.respond(endToEndId, request));
    }
}
