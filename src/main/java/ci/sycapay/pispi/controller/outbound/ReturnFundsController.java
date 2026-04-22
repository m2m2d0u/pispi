package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.returnfunds.ReturnAcceptRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsResponse;
import ci.sycapay.pispi.dto.returnfunds.ReturnRejectRequest;
import ci.sycapay.pispi.service.returnfunds.ReturnFundsService;
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
 * Return-of-funds (CAMT.056 / CAMT.029 / PACS.004) is a direct-participant
 * primitive — the reimbursement flow runs between settlement accounts held at
 * the central bank, which indirect participants (EMEs) don't have. Hidden from
 * OpenAPI/Swagger for indirect-participant deployments; the endpoints remain
 * callable for sponsoring-bank operators.
 */
@Hidden
@Tag(name = "Return Funds")
@Slf4j
@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnFundsController {

    private final ReturnFundsService service;

    @Operation(summary = "Request a return of funds",
               description = "Sends a CAMT.056 return-of-funds request to the AIP referencing a previously executed transfer by its endToEndId. The receiving PI may accept or reject.")
    @PostMapping
    public ResponseEntity<ApiResponse<ReturnFundsResponse>> requestReturn(
            @Valid @RequestBody ReturnFundsRequest request) {
        ReturnFundsResponse data = service.requestReturn(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Get a return request", description = "Retrieves the local state of a return-of-funds request by its unique identifier.")
    @GetMapping("/{identifiantDemande}")
    public ApiResponse<ReturnFundsResponse> getReturnRequest(@Parameter(description = "Unique identifier of the return request") @PathVariable String identifiantDemande) {
        return ApiResponse.ok(service.getReturnRequest(identifiantDemande));
    }

    @Operation(summary = "Accept an inbound return-of-funds request",
               description = "Executes the return by sending a PACS.004 to the AIP, transferring the specified amount back to the requesting participant.")
    @PostMapping("/incoming/{identifiantDemande}/accept")
    public ApiResponse<ReturnFundsResponse> acceptReturn(
            @PathVariable String identifiantDemande,
            @Valid @RequestBody ReturnAcceptRequest request) {
        return ApiResponse.ok(service.acceptReturn(identifiantDemande, request));
    }

    @Operation(summary = "Reject an inbound return-of-funds request",
               description = "Sends a CAMT.029 rejection to the AIP for an inbound return-of-funds request. Requires a rejection reason code.")
    @PostMapping("/incoming/{identifiantDemande}/reject")
    public ApiResponse<ReturnFundsResponse> rejectReturn(
            @PathVariable String identifiantDemande,
            @Valid @RequestBody ReturnRejectRequest request) {
        return ApiResponse.ok(service.rejectReturn(identifiantDemande, request));
    }
}
