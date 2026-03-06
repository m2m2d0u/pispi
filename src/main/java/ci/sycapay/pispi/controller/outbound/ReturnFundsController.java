package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.returnfunds.ReturnAcceptRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsResponse;
import ci.sycapay.pispi.dto.returnfunds.ReturnRejectRequest;
import ci.sycapay.pispi.service.returnfunds.ReturnFundsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnFundsController {

    private final ReturnFundsService service;

    @PostMapping
    public ResponseEntity<ApiResponse<ReturnFundsResponse>> requestReturn(
            @Valid @RequestBody ReturnFundsRequest request) {
        ReturnFundsResponse data = service.requestReturn(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @GetMapping("/{identifiantDemande}")
    public ApiResponse<ReturnFundsResponse> getReturnRequest(@PathVariable String identifiantDemande) {
        return ApiResponse.ok(service.getReturnRequest(identifiantDemande));
    }

    @PostMapping("/incoming/{identifiantDemande}/accept")
    public ApiResponse<ReturnFundsResponse> acceptReturn(
            @PathVariable String identifiantDemande,
            @Valid @RequestBody ReturnAcceptRequest request) {
        return ApiResponse.ok(service.acceptReturn(identifiantDemande, request));
    }

    @PostMapping("/incoming/{identifiantDemande}/reject")
    public ApiResponse<ReturnFundsResponse> rejectReturn(
            @PathVariable String identifiantDemande,
            @Valid @RequestBody ReturnRejectRequest request) {
        return ApiResponse.ok(service.rejectReturn(identifiantDemande, request));
    }
}
