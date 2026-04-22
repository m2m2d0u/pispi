package ci.sycapay.pispi.service.resolver;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiMessageLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Shared resolver that reconstructs a {@link ResolvedClient} from an inbound
 * RAC_SEARCH log entry. Used by the transfer and request-to-pay flows so they
 * can accept a lean request body (just {@code endToEndIdSearch*}) instead of
 * re-transmitting the client identity on every call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSearchResolver {

    private final PiMessageLogRepository messageLogRepository;
    private final ObjectMapper objectMapper;

    public ResolvedClient resolve(String endToEndIdSearch, String side) {
        PiMessageLog entry = messageLogRepository
                .findFirstByEndToEndIdAndDirectionAndMessageTypeOrderByCreatedAtDesc(
                        endToEndIdSearch, MessageDirection.INBOUND, IsoMessageType.RAC_SEARCH)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Résultat de recherche d'alias introuvable pour endToEndId", endToEndIdSearch));

        Map<String, Object> data;
        try {
            data = objectMapper.readValue(entry.getPayload(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de lire le payload du log [endToEndId="
                    + endToEndIdSearch + "]", e);
        }

        String nom = str(data, "nom");
        String telephone = str(data, "telephone");
        String paysResidence = str(data, "paysResidence");
        String categorie = str(data, "categorie");
        String other = str(data, "other");
        String typeCompteStr = str(data, "typeCompte");

        if (nom == null || telephone == null || paysResidence == null || categorie == null
                || other == null || typeCompteStr == null) {
            throw new IllegalStateException(
                    "Payload du résultat de recherche incomplet pour le " + side
                            + " [endToEndId=" + endToEndIdSearch + "]");
        }

        TypeClient typeClient;
        try {
            typeClient = TypeClient.valueOf(categorie);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Catégorie client inconnue: " + categorie);
        }

        TypeCompte typeCompte;
        try {
            typeCompte = TypeCompte.valueOf(typeCompteStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Type de compte inconnu: " + typeCompteStr);
        }

        ClientInfo.ClientInfoBuilder builder = ClientInfo.builder()
                .nom(nom)
                .typeClient(typeClient)
                .pays(paysResidence)
                .telephone(telephone);

        String genre = str(data, "genre");
        if (genre != null) builder.genre(genre);
        String adresse = str(data, "adresse");
        if (adresse != null) builder.adresse(adresse);
        String ville = str(data, "ville");
        if (ville != null) builder.ville(ville);
        String dateNaissance = str(data, "dateNaissance");
        if (dateNaissance != null) builder.dateNaissance(dateNaissance);
        String paysNaissance = str(data, "paysNaissance");
        if (paysNaissance != null) builder.paysNaissance(paysNaissance);
        String villeNaissance = str(data, "villeNaissance");
        if (villeNaissance != null) builder.lieuNaissance(villeNaissance);
        String nationalite = str(data, "nationalite");
        if (nationalite != null) builder.nationalite(nationalite);
        // The RAC_SEARCH log can carry identification under three different keys
        // depending on what the alias was registered with
        // (AliasCallbackService writes these). Take whichever is populated — in
        // priority order: national ID > passport > tax ID.
        String identificationNationale = str(data, "identificationNationale");
        String numeroPasseport = str(data, "numeroPasseport");
        String identificationFiscale = str(data, "identificationFiscale");
        if (identificationNationale != null) {
            builder.identifiant(identificationNationale)
                   .typeIdentifiant(CodeSystemeIdentification.NIDN);
        } else if (numeroPasseport != null) {
            builder.identifiant(numeroPasseport)
                   .typeIdentifiant(CodeSystemeIdentification.CCPT);
        } else if (identificationFiscale != null) {
            builder.identifiant(identificationFiscale)
                   .typeIdentifiant(CodeSystemeIdentification.TXID);
        }
        String email = str(data, "email");
        if (email != null) builder.email(email);

        String valeurAlias = str(data, "valeurAlias");
        String codeMembreParticipant = str(data, "participant");

        log.info("Client {} résolu depuis le log de recherche [endToEndId={}]", side, endToEndIdSearch);
        return new ResolvedClient(builder.build(), other, typeCompte, valeurAlias, codeMembreParticipant);
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : null;
    }
}
