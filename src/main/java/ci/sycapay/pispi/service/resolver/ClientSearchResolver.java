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

    /**
     * Resolve a participant from the latest inbound RAC_SEARCH log entry whose
     * payload carries the given {@code aliasValue}. Used by the mobile-facing
     * {@code POST /api/v1/transferts} flow where the client submits a raw
     * alias (not a pre-computed {@code endToEndIdSearch}) — we look the alias
     * up in the existing search log, grab its {@code endToEndId}, and delegate
     * to the primary {@link #resolve(String, String)} path so the parsing /
     * ClientInfo-building code stays in one place.
     *
     * <p>Throws {@link ResourceNotFoundException} if no RAC_SEARCH result is
     * on file for the alias — the caller is expected to emit a RAC_SEARCH
     * first (per §4.2, no caching means this is a single-use lookup).
     */
    public ResolvedClient resolveByAlias(String aliasValue, String side) {
        PiMessageLog entry = messageLogRepository
                .findLatestRacSearchByAliasValue(aliasValue)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun résultat de recherche d'alias (RAC_SEARCH) pour l'alias", aliasValue));
        return resolve(entry.getEndToEndId(), side);
    }

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

        // Identification routing for pacs.008 / pain.013 (spec §4.3.1.1 p.66-73).
        //
        // XSD path for ALL these fields is <Dbtr>/<Id>/<OrgId>/<Othr>, and the
        // BCEAO restricted schema caps <Othr> at maxOccurs=1 inside <OrgId>.
        // We therefore MUST pick exactly one identifier per side.
        //
        //   B / G (personnes morales):
        //     - identificationFiscale is the canonical TXID and is how PI-RAC
        //       keys the account. Emit it as numeroIdentification + systeme=TXID.
        //     - identificationFiscaleCommercant stays null — it's redundant for
        //       B/G (same value, same path) and emitting both triggers the
        //       "Element 'Othr': not expected" XSD rejection observed earlier.
        //     - If there's no fiscal but there's an RCCM, emit that as the
        //       sole identifier via numeroRCCMClient* (the builder picks that
        //       up when identificationFiscaleCommercant is null).
        //
        //   C (commerçant individuel):
        //     - Primary stays NIDN/CCPT (personal ID); identificationFiscale
        //       is the DISTINCT commercial fiscal ID, emitted as
        //       identificationFiscaleCommercant. Two different values map to
        //       the same XSD path — handled by the payload builder choosing
        //       one per-message (BE01 risk if PI-RAC disagrees on which).
        //
        //   P (personne physique, non commerçant):
        //     - Primary NIDN/CCPT; no commercial fiscal ID applies.
        String identificationFiscaleCommercant = null;

        switch (typeClient) {
            case B, G -> {
                if (identificationFiscale != null) {
                    // Canonical TXID — emit as numeroIdentification + systeme=TXID
                    // so PI-RAC can match against the account registration.
                    builder.identifiant(identificationFiscale)
                           .typeIdentifiant(CodeSystemeIdentification.TXID);
                    // identificationFiscaleCommercant stays null on purpose —
                    // see header comment; emitting both breaks the XSD.
                } else if (identificationRccm != null) {
                    // No fiscal ID — RCCM becomes the sole identifier.
                    // The payload builder picks up identificationRccm via the
                    // numeroRCCMClient* branch when identificationFiscale-
                    // Commercant is null (and we leave identifiant null here
                    // to avoid another double-<Othr>).
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
