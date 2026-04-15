package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.transfer.ParticipantDto;
import ci.sycapay.pispi.service.participant.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Participants")
@Slf4j
@RestController
@RequestMapping("/api/v1/participants")
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService service;

    @Operation(summary = "List all known participants", description = "Returns all SPI participants cached in the local database. Call /sync first to refresh from the AIP.")
    @GetMapping
    public ApiResponse<List<ParticipantDto>> listParticipants() {
        return ApiResponse.ok(service.listParticipants());
    }

    @Operation(summary = "Synchronise participant directory",
               description = "Sends a CAMT.013 request to the AIP to retrieve the full participant list. The AIP responds asynchronously via callback which updates the local database.")
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncParticipants() {
        service.syncParticipants();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @Operation(summary = "Get a participant by member code", description = "Retrieves a single participant from the local cache using their 6-character SPI member code.")
    @GetMapping("/{codeMembre}")
    public ApiResponse<ParticipantDto> getParticipant(@Parameter(description = "6-character SPI member code of the participant") @PathVariable String codeMembre) {
        return ApiResponse.ok(service.getParticipant(codeMembre));
    }
}
