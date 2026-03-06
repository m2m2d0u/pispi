package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.returnfunds.ReturnAcceptRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsResponse;
import ci.sycapay.pispi.dto.returnfunds.ReturnRejectRequest;
import ci.sycapay.pispi.service.returnfunds.ReturnFundsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnFundsController {

    private final ReturnFundsService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReturnFundsResponse requestReturn(@Valid @RequestBody ReturnFundsRequest request) {
        return service.requestReturn(request);
    }

    @GetMapping("/{identifiantDemande}")
    public ReturnFundsResponse getReturnRequest(@PathVariable String identifiantDemande) {
        return service.getReturnRequest(identifiantDemande);
    }

    @PostMapping("/incoming/{identifiantDemande}/accept")
    public ReturnFundsResponse acceptReturn(@PathVariable String identifiantDemande,
                                            @Valid @RequestBody ReturnAcceptRequest request) {
        return service.acceptReturn(identifiantDemande, request);
    }

    @PostMapping("/incoming/{identifiantDemande}/reject")
    public ReturnFundsResponse rejectReturn(@PathVariable String identifiantDemande,
                                            @Valid @RequestBody ReturnRejectRequest request) {
        return service.rejectReturn(identifiantDemande, request);
    }
}
