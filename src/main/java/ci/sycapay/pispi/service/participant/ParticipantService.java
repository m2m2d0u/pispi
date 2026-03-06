package ci.sycapay.pispi.service.participant;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.transfer.ParticipantDto;
import ci.sycapay.pispi.entity.PiParticipant;
import ci.sycapay.pispi.enums.EtatParticipant;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TypeParticipant;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiParticipantRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final PiParticipantRepository repository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    public List<ParticipantDto> listParticipants() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public ParticipantDto getParticipant(String codeMembre) {
        PiParticipant p = repository.findByCodeMembre(codeMembre)
                .orElseThrow(() -> new ResourceNotFoundException("Participant", codeMembre));
        return toDto(p);
    }

    @Transactional
    public void syncParticipants() {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> camt013 = new HashMap<>();
        camt013.put("msgId", msgId);
        camt013.put("requete", "ALLL");

        messageLogService.log(msgId, null, IsoMessageType.CAMT_013, MessageDirection.OUTBOUND, camt013, null, null);
        aipClient.post("/api/spi/v{version}/participant", camt013);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void processParticipantList(Map<String, Object> camt014) {
        List<Map<String, Object>> participants = (List<Map<String, Object>>) camt014.get("listeParticipant");
        if (participants == null) return;

        for (Map<String, Object> p : participants) {
            String code = String.valueOf(p.get("codeMembreParticipant"));
            PiParticipant entity = repository.findByCodeMembre(code)
                    .orElse(PiParticipant.builder().codeMembre(code).build());

            entity.setNom(String.valueOf(p.get("nomParticipant")));
            entity.setEtat(EtatParticipant.valueOf(String.valueOf(p.get("etatParticipant"))));
            entity.setTypeParticipant(TypeParticipant.valueOf(String.valueOf(p.get("typeParticipant"))));
            entity.setPays(String.valueOf(p.get("paysParticipant")));
            entity.setParticipantSponsor((String) p.get("participantSponsor"));
            entity.setLastSyncedAt(LocalDateTime.now());
            repository.save(entity);
        }
    }

    private ParticipantDto toDto(PiParticipant p) {
        return ParticipantDto.builder()
                .codeMembreParticipant(p.getCodeMembre())
                .nomParticipant(p.getNom())
                .etatParticipant(p.getEtat())
                .typeParticipant(p.getTypeParticipant())
                .paysParticipant(p.getPays())
                .participantSponsor(p.getParticipantSponsor())
                .build();
    }
}
