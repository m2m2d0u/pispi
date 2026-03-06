package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.transfer.ParticipantDto;
import ci.sycapay.pispi.service.participant.ParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/participants")
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService service;

    @GetMapping
    public List<ParticipantDto> listParticipants() {
        return service.listParticipants();
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void syncParticipants() {
        service.syncParticipants();
    }

    @GetMapping("/{codeMembre}")
    public ParticipantDto getParticipant(@PathVariable String codeMembre) {
        return service.getParticipant(codeMembre);
    }
}
