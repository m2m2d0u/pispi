package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.transfer.IdentityVerificationRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationRespondRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationResponse;
import ci.sycapay.pispi.service.transfer.IdentityVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/verifications")
@RequiredArgsConstructor
public class IdentityVerificationController {

    private final IdentityVerificationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IdentityVerificationResponse requestVerification(@Valid @RequestBody IdentityVerificationRequest request) {
        return service.requestVerification(request);
    }

    @GetMapping("/{endToEndId}")
    public IdentityVerificationResponse getVerification(@PathVariable String endToEndId) {
        return service.getVerification(endToEndId);
    }

    @PostMapping("/incoming/{endToEndId}/respond")
    public IdentityVerificationResponse respond(@PathVariable String endToEndId,
                                                 @Valid @RequestBody IdentityVerificationRespondRequest request) {
        return service.respond(endToEndId, request);
    }
}
