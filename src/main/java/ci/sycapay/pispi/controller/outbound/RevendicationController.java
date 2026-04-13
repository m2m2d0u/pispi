package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.alias.RevendicationRequest;
import ci.sycapay.pispi.dto.alias.RevendicationResponse;
import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.service.alias.RevendicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Revendications")
@RestController
@RequestMapping("/api/v1/revendications")
@RequiredArgsConstructor
public class RevendicationController {

    private final RevendicationService service;

    @PostMapping
    public ResponseEntity<ApiResponse<RevendicationResponse>> initiateClaim(
            @Valid @RequestBody RevendicationRequest request) {
        RevendicationResponse data = service.initiateClaim(request.getAlias());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @GetMapping("/{identifiantRevendication}")
    public ApiResponse<RevendicationResponse> getClaimStatus(@PathVariable String identifiantRevendication) {
        return ApiResponse.ok(service.getClaimStatus(identifiantRevendication));
    }

    @PostMapping("/incoming/{id}/accept")
    public ApiResponse<RevendicationResponse> acceptClaim(@PathVariable String id) {
        return ApiResponse.ok(service.acceptClaim(id));
    }

    @PostMapping("/incoming/{id}/reject")
    public ApiResponse<RevendicationResponse> rejectClaim(@PathVariable String id) {
        return ApiResponse.ok(service.rejectClaim(id));
    }
}
