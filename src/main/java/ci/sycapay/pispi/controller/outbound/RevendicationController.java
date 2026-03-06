package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.alias.RevendicationRequest;
import ci.sycapay.pispi.dto.alias.RevendicationResponse;
import ci.sycapay.pispi.service.alias.RevendicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revendications")
@RequiredArgsConstructor
public class RevendicationController {

    private final RevendicationService service;

    @PostMapping
    public RevendicationResponse initiateClaim(@Valid @RequestBody RevendicationRequest request) {
        return service.initiateClaim(request.getAlias());
    }

    @GetMapping("/{identifiantRevendication}")
    public RevendicationResponse getClaimStatus(@PathVariable String identifiantRevendication) {
        return service.getClaimStatus(identifiantRevendication);
    }

    @PostMapping("/incoming/{id}/accept")
    public RevendicationResponse acceptClaim(@PathVariable String id) {
        return service.acceptClaim(id);
    }

    @PostMapping("/incoming/{id}/reject")
    public RevendicationResponse rejectClaim(@PathVariable String id) {
        return service.rejectClaim(id);
    }
}
