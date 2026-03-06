package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.transfer.ParticipantDto;
import ci.sycapay.pispi.service.participant.ParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/participants")
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService service;

    @GetMapping
    public ApiResponse<List<ParticipantDto>> listParticipants() {
        return ApiResponse.ok(service.listParticipants());
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncParticipants() {
        service.syncParticipants();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @GetMapping("/{codeMembre}")
    public ApiResponse<ParticipantDto> getParticipant(@PathVariable String codeMembre) {
        return ApiResponse.ok(service.getParticipant(codeMembre));
    }
}
