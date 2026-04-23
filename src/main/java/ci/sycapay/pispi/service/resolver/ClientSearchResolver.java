package ci.sycapay.pispi.service.resolver;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.exception.InvalidStateException;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiAliasRepository;
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
    private final PiAliasRepository aliasRepository;
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
        // Identification must match what's registered at PI-RAC for this
        // account, otherwise the AIP rejects the pacs.008 / pain.013 with
        // BE01 "InconsistenWithEndCustomer". Since §4.1 we don't hold a
        // local copy of the registered ID — we read it back from the
        // RAC_SEARCH log — but we MUST pick the right key by client type:
        //
        //   B / G (personnes morales)        → identificationFiscale (TXID)
        //   P / C (personnes physiques)      → identificationNationale (NIDN),
        //                                       numeroPasseport (CCPT) as fallback
        //
        // The RAC_SEARCH payload may contain more than one of these fields
        // (e.g. a mandataire's NIDN on a business account). Picking the
        // wrong one is exactly what triggers BE01, so the selection is
        // driven by typeClient, not by which keys happen to be non-null.
        String identificationNationale = str(data, "identificationNationale");
        String numeroPasseport = str(data, "numeroPasseport");
        String identificationFiscale = str(data, "identificationFiscale");
        String identificationRccm = str(data, "identificationRccm");

        // identificationFiscaleCommercant* is required in pacs.008 / pain.013 when the
        // client provided their fiscal/RCCM ID at alias enrollment (spec §4.3.1.1 p.72-73).
        //   B / G: identificationFiscale is BOTH the primary TXID AND the commercial fiscal ID.
        //   C:     identificationFiscale is only the commercial fiscal ID (primary stays NIDN/CCPT).
        String identificationFiscaleCommercant = null;

        switch (typeClient) {
            case B, G -> {
                if (identificationFiscale != null) {
                    builder.identifiant(identificationFiscale)
                           .typeIdentifiant(CodeSystemeIdentification.TXID);
                    identificationFiscaleCommercant = identificationFiscale;
                } else if (identificationRccm != null) {
                    // RCCM is passed to ResolvedClient and picked up as numeroRCCMClient* in
                    // the payload builder (identificationFiscaleCommercant stays null so the
                    // else-if branch fires).
                } else {
                    log.warn("Client {} (type {}) has no identificationFiscale in the RAC_SEARCH "
                                    + "log — pacs.008 / pain.013 will fail BE01 "
                                    + "(InconsistenWithEndCustomer). endToEndIdSearch={}",
                            side, typeClient, endToEndIdSearch);
                }
            }
            case P, C -> {
                if (identificationNationale != null) {
                    builder.identifiant(identificationNationale)
                           .typeIdentifiant(CodeSystemeIdentification.NIDN);
                } else if (numeroPasseport != null) {
                    builder.identifiant(numeroPasseport)
                           .typeIdentifiant(CodeSystemeIdentification.CCPT);
                } else if (identificationFiscale != null) {
                    // Last-resort fallback — BCEAO v2.0.2 allows a type C
                    // (commerçant individuel) to carry a TXID on top of NIDN,
                    // but the account is keyed by the personal ID for P.
                    // Use TXID only when nothing else is available so the
                    // pacs.008 isn't empty.
                    builder.identifiant(identificationFiscale)
                           .typeIdentifiant(CodeSystemeIdentification.TXID);
                }
                // Type C: identificationFiscale is ALSO the commercial fiscal ID
                // (identificationFiscaleCommercant* in pacs.008 / pain.013).
                // identificationRccm is the RCCM variant (mutually exclusive).
                if (typeClient == TypeClient.C) {
                    identificationFiscaleCommercant = identificationFiscale;
                    builder.identificationRccm(identificationRccm);
                }
            }
        }
        String email = str(data, "email");
        if (email != null) builder.email(email);

        String valeurAlias = str(data, "valeurAlias");
        String codeMembreParticipant = str(data, "participant");
        String iban = str(data, "iban");

        // Per BCEAO PI-RAC §3.2, a LOCKED alias must not carry any payment.
        // When the resolved alias corresponds to a local row (we may be the
        // détenteur, OR have cached the alias for another reason) and it is
        // LOCKED, refuse the operation here rather than emit a message the
        // AIP would reject.
        if (valeurAlias != null) {
            aliasRepository.findByAliasValue(valeurAlias).ifPresent(local -> {
                if (local.getStatut() == AliasStatus.LOCKED) {
                    throw new InvalidStateException(
                            "Alias " + valeurAlias + " is LOCKED (revendication en cours) — "
                                    + "no payment can be initiated against it until the claim resolves.");
                }
            });
        }

        log.info("Client {} résolu depuis le log de recherche [endToEndId={}]", side, endToEndIdSearch);
        return new ResolvedClient(builder.build(), other, iban, typeCompte, valeurAlias,
                codeMembreParticipant, identificationFiscaleCommercant, identificationRccm);
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : null;
    }
}
