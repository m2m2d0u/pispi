package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.alias.RevendicationRequest;
import ci.sycapay.pispi.dto.alias.RevendicationResponse;
import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.service.alias.RevendicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Revendications")
@Slf4j
@RestController
@RequestMapping("/api/v1/revendications")
@RequiredArgsConstructor
public class RevendicationController {

    private final RevendicationService service;

    @Operation(summary = "Initiate an alias ownership claim",
               description = "Starts a revendication process to claim ownership of an alias currently held by another participant. The AIP assigns an identifiantRevendication.")
    @PostMapping
    public ResponseEntity<ApiResponse<RevendicationResponse>> initiateClaim(
            @Valid @RequestBody RevendicationRequest request) {
        RevendicationResponse data = service.initiateClaim(request.getAlias());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Get claim status", description = "Fetches the current status of a revendication from the AIP and returns the locally stored record.")
    @GetMapping("/{identifiantRevendication}")
    public ApiResponse<RevendicationResponse> getClaimStatus(@Parameter(description = "Unique identifier of the revendication assigned by the AIP") @PathVariable String identifiantRevendication) {
        return ApiResponse.ok(service.getClaimStatus(identifiantRevendication));
    }

    @Operation(summary = "Accept an inbound alias claim",
            description = "Accepts a revendication initiated by another participant targeting an "
                    + "alias owned by this PI. Per BCEAO PI-RAC v3.0.0 §3.3, the acceptance must "
                    + "declare whether it came from the CLIENT (holder explicitly accepted) or "
                    + "from the PARTICIPANT (default; also used by the day-14 auto-accept job).")
    @PostMapping("/incoming/{id}/accept")
    public ApiResponse<RevendicationResponse> acceptClaim(
            @Parameter(description = "Revendication identifier") @PathVariable String id,
            @Parameter(description = "Author of the acceptance: CLIENT or PARTICIPANT (defaults to PARTICIPANT)")
            @RequestParam(defaultValue = "PARTICIPANT") String auteurAction) {
        return ApiResponse.ok(service.acceptClaim(id, auteurAction));
    }

    @Operation(summary = "Reject an inbound alias claim", description = "Rejects a revendication initiated by another participant. The alias remains with the current owner.")
    @PostMapping("/incoming/{id}/reject")
    public ApiResponse<RevendicationResponse> rejectClaim(@Parameter(description = "Revendication identifier") @PathVariable String id) {
        return ApiResponse.ok(service.rejectClaim(id));
    }
}
