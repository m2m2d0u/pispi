package ci.sycapay.pispi.service.rtp;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.dto.rtp.RequestToPayRequest;
import ci.sycapay.pispi.dto.rtp.RequestToPayResponse;
import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.CanalCommunicationRtp;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.RtpStatus;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.resolver.ClientSearchResolver;
import ci.sycapay.pispi.service.resolver.ResolvedClient;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.normaliseIsoInstantMillis;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestToPayService {

    private final PiRequestToPayRepository rtpRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;
    private final ClientSearchResolver clientSearchResolver;
    private final ObjectMapper objectMapper;

    /**
     * Emit an outbound PAIN.013 Request-to-Pay toward the AIP, using the flat
     * BCEAO {@code DemandePaiement} schema.
     *
     * <p>Both parties are resolved from inbound RAC_SEARCH log entries
     * ({@code endToEndIdSearchPayeur} / {@code endToEndIdSearchPaye}) — the
     * caller passes only the payment-specific fields (amount, canal, motif,
     * etc.), the identity data comes from {@code pi_message_log}.
     *
     * <p>Per BCEAO, numbers and booleans are serialised as strings
     * ({@code "\d+"} / {@code "true|false"}).
     */
    @Transactional
    public RequestToPayResponse createRtp(RequestToPayRequest request) {
        ResolvedClient payeur = clientSearchResolver.resolve(
                request.getEndToEndIdSearchPayeur(), "payeur");
        ResolvedClient paye = clientSearchResolver.resolve(
                request.getEndToEndIdSearchPaye(), "paye");

        validateTypeComptePaye(paye);
        validatePayeRequiredFields(paye);
        validateCanalDateRules(request);

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        // Per BCEAO spec, the pain.013 endToEndId is the one generated at alias-search
        // time: the payé searched the payeur's alias — that search's endToEndId ties
        // the RTP back to its originating RAC_SEARCH entry in pi_message_log.
        String endToEndId = request.getEndToEndIdSearchPayeur();

        Map<String, Object> pain013 = buildPain013Payload(msgId, endToEndId, request, payeur, paye);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_013,
                MessageDirection.OUTBOUND, pain013, null, null);

        PiRequestToPay rtp = buildEntity(msgId, endToEndId, codeMembre, request, payeur, paye);
        rtpRepository.save(rtp);

        log.info("RTP PAIN.013 emitted: endToEndId={} payeur={} paye={}",
                endToEndId, payeur.codeMembre(), codeMembre);
        log.info("RTP PAIN.013 payload: {}", objectMapper.writeValueAsString(pain013));
        aipClient.post("/demandes-paiements", pain013);

        return toResponse(rtp);
    }

    public RequestToPayResponse getRtp(String endToEndId) {
        PiRequestToPay rtp = rtpRepository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("RTP", endToEndId));
        return toResponse(rtp);
    }

    public Page<RequestToPayResponse> listRtps(Pageable pageable) {
        return rtpRepository.findAll(pageable)
                .map(this::toResponse);
    }

    /**
     * Emit a PAIN.014 rejection (BCEAO {@code ReponseDemandePaiement}) for an
     * inbound RTP received on this PI. The spec mandates {@code statut: "RJCT"}
     * and a {@code codeRaison} matching {@code [A-Z]{2}\d{2}}.
     */
    @Transactional
    public RequestToPayResponse rejectRtp(String endToEndId, String codeRaison) {
        PiRequestToPay rtp = rtpRepository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("RTP", endToEndId));

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> pain014 = new HashMap<>();
        pain014.put("msgId", msgId);
        pain014.put("msgIdDemande", rtp.getMsgId());
        pain014.put("identifiantDemandePaiement", rtp.getIdentifiantDemandePaiement());
        pain014.put("endToEndId", endToEndId);
        pain014.put("statut", "RJCT");
        pain014.put("codeRaison", codeRaison);
        if (notBlank(rtp.getReferenceBulk())) pain014.put("referenceBulk", rtp.getReferenceBulk());

        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_014,
                MessageDirection.OUTBOUND, pain014, null, null);
        aipClient.post("/demandes-paiements/reponses", pain014);

        rtp.setStatut(RtpStatus.RJCT);
        rtp.setCodeRaison(codeRaison);
        rtp.setMsgIdReponse(msgId);
        rtpRepository.save(rtp);

        return toResponse(rtp);
    }

    // =======================================================================
    // BCEAO validation
    // =======================================================================

    /**
     * BCEAO {@code typeCompteClientPaye} pattern is
     * {@code CACC|SVGS|TRAN|LLSV|VACC|TAXE} — {@code TRAL} is forbidden on the
     * payé (unlike the payeur).
     */
    private static void validateTypeComptePaye(ResolvedClient paye) {
        if (paye.typeCompte() == TypeCompte.TRAL) {
            throw new IllegalArgumentException(
                    "typeCompteClientPaye résolu est TRAL, ce qui est interdit par le schéma "
                            + "BCEAO DemandePaiement (CACC|SVGS|TRAN|LLSV|VACC|TAXE). "
                            + "Le compte du payé doit être d'un type supporté.");
        }
    }


    /**
     * BCEAO canal-specific rules on date fields:
     * <ul>
     *   <li>{@code dateHeureExecution} (ReqdExctnDt) is REQUIRED for FACTURE (401)
     *       and E_COMMERCE_LIVRAISON (520), FORBIDDEN for MARCHAND_SUR_SITE (500),
     *       E_COMMERCE_IMMEDIAT (521), PARTICULIER (631).</li>
     *   <li>{@code dateHeureLimiteAction} (XpryDt) is FORBIDDEN for
     *       MARCHAND_SUR_SITE (500), E_COMMERCE_IMMEDIAT (521), PARTICULIER (631).</li>
     * </ul>
     */
    private static void validateCanalDateRules(RequestToPayRequest req) {
        CanalCommunicationRtp canal = req.getCanalCommunication();
        boolean execProvided = notBlank(req.getDateHeureExecution());
        boolean limiteProvided = notBlank(req.getDateHeureLimiteAction());

        switch (canal) {
            case FACTURE, E_COMMERCE_LIVRAISON -> {
                if (!execProvided) {
                    throw new IllegalArgumentException(
                            "dateHeureExecution est obligatoire pour le canal "
                                    + canal.name() + " (code " + canal.getCode() + ")");
                }
            }
            case MARCHAND_SUR_SITE, E_COMMERCE_IMMEDIAT, PARTICULIER -> {
                if (execProvided) {
                    throw new IllegalArgumentException(
                            "dateHeureExecution ne doit pas être renseigné pour le canal "
                                    + canal.name() + " (code " + canal.getCode() + ")");
                }
                if (limiteProvided) {
                    throw new IllegalArgumentException(
                            "dateHeureLimiteAction ne doit pas être renseigné pour le canal "
                                    + canal.name() + " (code " + canal.getCode() + ")");
                }
            }
        }

        // BCEAO rule: XpryDt (dateHeureLimiteAction) must be >= ReqdExctnDt
        // (dateHeureExecution). The limit action acts as the expiry of the
        // request's validity window, which sits after the requested execution.
        if (execProvided && limiteProvided) {
            try {
                java.time.Instant exec = java.time.Instant.parse(req.getDateHeureExecution());
                java.time.Instant limite = java.time.Instant.parse(req.getDateHeureLimiteAction());
                if (limite.isBefore(exec)) {
                    throw new IllegalArgumentException(
                            "dateHeureLimiteAction (" + req.getDateHeureLimiteAction()
                                    + ") doit être supérieure ou égale à dateHeureExecution ("
                                    + req.getDateHeureExecution() + ") — règle BCEAO XpryDt ≥ ReqdExctnDt.");
                }
            } catch (java.time.format.DateTimeParseException e) {
                // Skip ordering check if either date is not strict ISO-8601 —
                // the normaliser / AIP will surface the format error separately.
            }
        }
    }

    /**
     * BCEAO {@code DemandePaiement} marks several payé fields as required —
     * notably {@code numeroIdentificationClientPaye} and
     * {@code systemeIdentificationClientPaye}. If the RAC_SEARCH log resolved
     * for the payé carries none of {@code identificationNationale} /
     * {@code numeroPasseport} / {@code identificationFiscale}, we cannot build a
     * valid PAIN.013. Fail fast with a clear 400 instead of emitting and
     * letting the AIP reject with
     * {@code "numeroIdentificationClientPaye doit être renseigné"}.
     */
    private static void validatePayeRequiredFields(ResolvedClient paye) {
        ClientInfo info = paye.clientInfo();
        if (info.getIdentifiant() == null || info.getIdentifiant().isBlank()
                || info.getTypeIdentifiant() == null) {
            throw new IllegalArgumentException(
                    "L'alias payé résolu ne porte aucun identifiant (identificationNationale / "
                            + "numeroPasseport / identificationFiscale). BCEAO DemandePaiement "
                            + "exige numeroIdentificationClientPaye + systemeIdentificationClientPaye. "
                            + "Ré-enregistrer l'alias avec une identification (NIDN / CCPT / TXID) ou "
                            + "choisir un autre alias comme bénéficiaire.");
        }
        if (info.getVille() == null || info.getVille().isBlank()) {
            throw new IllegalArgumentException(
                    "L'alias payé résolu n'a pas de ville (villeClientPaye est obligatoire "
                            + "selon BCEAO DemandePaiement). Compléter l'alias ou utiliser un "
                            + "bénéficiaire avec ville renseignée.");
        }
    }

    // =======================================================================
    // PAIN.013 payload builder (BCEAO DemandePaiement — flat schema)
    // =======================================================================

    private Map<String, Object> buildPain013Payload(String msgId,
                                                    String endToEndId,
                                                    RequestToPayRequest request,
                                                    ResolvedClient payeur,
                                                    ResolvedClient paye) {
        Map<String, Object> p = new HashMap<>();
        p.put("msgId", msgId);
        p.put("endToEndId", endToEndId);
        p.put("identifiantDemandePaiement", request.getIdentifiantDemandePaiement());

        // BCEAO pain.013 InitgPty>Nm rule: must be "X" or an https:// URL.
        // Default to "X" when absent so the AIP doesn't reject with
        // "InitgPty>Nm doit contenir X si c'est le nom du client payé ou une
        // le lien vers une image commencant par https://".
        p.put("clientDemandeur", resolveClientDemandeur(request.getClientDemandeur()));
        // BCEAO pain.013 XSD enforces pattern \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z
        // on DtTm elements — millisecond precision is mandatory. Normalise input.
        putIfNotBlank(p, "dateHeureExecution",
                normaliseIsoInstantMillis(request.getDateHeureExecution()));
        putIfNotBlank(p, "dateHeureLimiteAction",
                normaliseIsoInstantMillis(request.getDateHeureLimiteAction()));
        // referenceBulk OMITTED from outbound: BCEAO AIP maps it into
        // RmtInf/Strd, which conflicts with motif → RmtInf/Ustrd (error
        // "Element 'Strd': This element is not expected"). Kept on the entity
        // for local traceability only — not emitted on the wire.

        // Canal & monetary (BCEAO encodes numbers/booleans as strings).
        p.put("canalCommunication", request.getCanalCommunication().getCode());
        p.put("montant", request.getMontant().toBigInteger().toString());
        if (request.getAutorisationModificationMontant() != null) {
            p.put("autorisationModificationMontant",
                    Boolean.toString(request.getAutorisationModificationMontant()));
        }
        if (request.getMontantRemisePaiementImmediat() != null) {
            p.put("montantRemisePaiementImmediat",
                    request.getMontantRemisePaiementImmediat().toBigInteger().toString());
        }
        if (request.getTauxRemisePaiementImmediat() != null) {
            p.put("tauxRemisePaiementImmediat",
                    request.getTauxRemisePaiementImmediat().toBigInteger().toString());
        }

        // Mandate (optional).
        putIfNotBlank(p, "identifiantMandat", request.getIdentifiantMandat());
        putIfNotBlank(p, "signatureNumeriqueMandat", request.getSignatureNumeriqueMandat());

        // Payeur party — flat fields (resolved).
        ClientInfo payeurInfo = payeur.clientInfo();
        p.put("nomClientPayeur", payeurInfo.getNom());
        p.put("typeClientPayeur", payeurInfo.getTypeClient().name());
        putIfNotBlank(p, "villeClientPayeur", payeurInfo.getVille());
        putIfNotBlank(p, "adresseClientPayeur", payeurInfo.getAdresse());
        putIfNotBlank(p, "numeroIdentificationClientPayeur", payeurInfo.getIdentifiant());
        if (payeurInfo.getTypeIdentifiant() != null) {
            p.put("systemeIdentificationClientPayeur", payeurInfo.getTypeIdentifiant().name());
        }
        putIfNotBlank(p, "dateNaissanceClientPayeur", payeurInfo.getDateNaissance());
        putIfNotBlank(p, "villeNaissanceClientPayeur", payeurInfo.getLieuNaissance());
        putIfNotBlank(p, "paysNaissanceClientPayeur", payeurInfo.getPaysNaissance());
        putIfNotBlank(p, "paysClientPayeur", payeurInfo.getPays());
        // IBAN vs other: send the correct account field per §4.1.4.2 spec.
        if (payeur.iban() != null) {
            p.put("ibanClientPayeur", payeur.iban());
        } else {
            putIfNotBlank(p, "otherClientPayeur", payeur.other());
        }
        p.put("typeCompteClientPayeur", payeur.typeCompte().name());
        p.put("deviseCompteClientPayeur", "XOF");
        putIfNotBlank(p, "aliasClientPayeur", payeur.aliasValue());
        putIfNotBlank(p, "codeMembreParticipantPayeur", payeur.codeMembre());
        // Type C commercial IDs: prefer resolved from RAC_SEARCH, fall back to caller override
        String idFiscCommPayeur = payeur.identificationFiscaleCommercant() != null
                ? payeur.identificationFiscaleCommercant()
                : request.getIdentificationFiscaleCommercantPayeur();
        if (idFiscCommPayeur != null) {
            p.put("identificationFiscaleCommercantPayeur", idFiscCommPayeur);
        } else if (payeur.identificationRccm() != null) {
            p.put("numeroRCCMClientPayeur", payeur.identificationRccm());
        }

        // Payé party — flat fields (resolved).
        ClientInfo payeInfo = paye.clientInfo();
        p.put("nomClientPaye", payeInfo.getNom());
        p.put("typeClientPaye", payeInfo.getTypeClient().name());
        putIfNotBlank(p, "villeClientPaye", payeInfo.getVille());
        putIfNotBlank(p, "latitudeClientPaye", request.getLatitudeClientPaye());
        putIfNotBlank(p, "longitudeClientPaye", request.getLongitudeClientPaye());
        putIfNotBlank(p, "adresseClientPaye", payeInfo.getAdresse());
        putIfNotBlank(p, "numeroIdentificationClientPaye", payeInfo.getIdentifiant());
        if (payeInfo.getTypeIdentifiant() != null) {
            p.put("systemeIdentificationClientPaye", payeInfo.getTypeIdentifiant().name());
        }
        putIfNotBlank(p, "dateNaissanceClientPaye", payeInfo.getDateNaissance());
        putIfNotBlank(p, "villeNaissanceClientPaye", payeInfo.getLieuNaissance());
        putIfNotBlank(p, "paysNaissanceClientPaye", payeInfo.getPaysNaissance());
        putIfNotBlank(p, "paysClientPaye", payeInfo.getPays());
        if (paye.iban() != null) {
            p.put("ibanClientPaye", paye.iban());
        } else {
            putIfNotBlank(p, "otherClientPaye", paye.other());
        }
        p.put("typeCompteClientPaye", paye.typeCompte().name());
        p.put("deviseCompteClientPaye", "XOF");
        putIfNotBlank(p, "aliasClientPaye", paye.aliasValue());
        String idFiscCommPaye = paye.identificationFiscaleCommercant() != null
                ? paye.identificationFiscaleCommercant()
                : request.getIdentificationFiscaleCommercantPaye();
        if (idFiscCommPaye != null) {
            p.put("identificationFiscaleCommercantPaye", idFiscCommPaye);
        } else if (paye.identificationRccm() != null) {
            p.put("numeroRCCMClientPaye", paye.identificationRccm());
        }

        // -----------------------------------------------------------------
        // RmtInf mutual exclusion — BCEAO pain.013 XSD accepts EITHER
        //   RmtInf/Ustrd   (filled by motif)
        //   RmtInf/Strd    (filled by montantRetrait, fraisRetrait,
        //                   montantAchat, typeDocumentReference,
        //                   numeroDocumentReference, referenceBulk)
        // …never both. The BCEAO PICASH reference XML confirms the
        // cash-withdrawal flow relies on Strd/RfrdDocInf/LineDtls with the
        // <Prtry>PICASH</Prtry> marker plus DuePyblAmt (montantRetrait) and
        // AdjstmntAmtAndRsn (fraisRetrait). If the caller supplies any Strd
        // field we therefore DROP motif, and if only motif is present we
        // drop the Strd-mappers. The entity keeps every field regardless.
        // -----------------------------------------------------------------
        boolean hasStrd =
                request.getMontantRetrait() != null
                || request.getFraisRetrait() != null
                || request.getMontantAchat() != null
                || request.getTypeDocumentReference() != null
                || notBlank(request.getNumeroDocumentReference());

        if (hasStrd) {
            if (request.getMontantRetrait() != null) {
                p.put("montantRetrait", request.getMontantRetrait().toBigInteger().toString());
            }
            if (request.getFraisRetrait() != null) {
                p.put("fraisRetrait", request.getFraisRetrait().toBigInteger().toString());
            }
            if (request.getMontantAchat() != null) {
                p.put("montantAchat", request.getMontantAchat().toBigInteger().toString());
            }
            if (request.getTypeDocumentReference() != null) {
                p.put("typeDocumentReference", request.getTypeDocumentReference().name());
            }
            putIfNotBlank(p, "numeroDocumentReference", request.getNumeroDocumentReference());
            // motif intentionally OMITTED when Strd fields are present.
        } else {
            // No Strd fields → motif is free to land in Ustrd.
            putIfNotBlank(p, "motif", request.getMotif());
        }

        return p;
    }

    // =======================================================================
    // Entity builder — persists the resolved flat BCEAO fields.
    // =======================================================================

    private PiRequestToPay buildEntity(String msgId,
                                       String endToEndId,
                                       String codeMembrePaye,
                                       RequestToPayRequest request,
                                       ResolvedClient payeur,
                                       ResolvedClient paye) {
        ClientInfo payeurInfo = payeur.clientInfo();
        ClientInfo payeInfo = paye.clientInfo();

        return PiRequestToPay.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .identifiantDemandePaiement(request.getIdentifiantDemandePaiement())
                .referenceBulk(request.getReferenceBulk())
                .direction(MessageDirection.OUTBOUND)
                .canalCommunication(request.getCanalCommunication())
                .montant(request.getMontant())
                .devise("XOF")
                .dateHeureLimiteAction(parseDateTime(request.getDateHeureLimiteAction()))
                // Payeur
                .codeMembrePayeur(payeur.codeMembre())
                .aliasClientPayeur(payeur.aliasValue())
                .otherClientPayeur(payeur.other())
                .typeComptePayeur(payeur.typeCompte())
                .nomClientPayeur(payeurInfo.getNom())
                .telephonePayeur(payeurInfo.getTelephone())
                .typeClientPayeur(payeurInfo.getTypeClient())
                .villeClientPayeur(payeurInfo.getVille())
                .paysClientPayeur(payeurInfo.getPays())
                .numeroIdentificationPayeur(payeurInfo.getIdentifiant())
                .systemeIdentificationPayeur(payeurInfo.getTypeIdentifiant())
                .dateNaissancePayeur(payeurInfo.getDateNaissance())
                .villeNaissancePayeur(payeurInfo.getLieuNaissance())
                .paysNaissancePayeur(payeurInfo.getPaysNaissance())
                .identificationFiscalePayeur(request.getIdentificationFiscaleCommercantPayeur())
                // Payé (this PI)
                .codeMembrePaye(codeMembrePaye)
                .aliasClientPaye(paye.aliasValue())
                .otherClientPaye(paye.other())
                .typeComptePaye(paye.typeCompte())
                .nomClientPaye(payeInfo.getNom())
                .telephonePaye(payeInfo.getTelephone())
                .typeClientPaye(payeInfo.getTypeClient())
                .villeClientPaye(payeInfo.getVille())
                .paysClientPaye(payeInfo.getPays())
                .numeroIdentificationPaye(payeInfo.getIdentifiant())
                .systemeIdentificationPaye(payeInfo.getTypeIdentifiant())
                .dateNaissancePaye(payeInfo.getDateNaissance())
                .villeNaissancePaye(payeInfo.getLieuNaissance())
                .paysNaissancePaye(payeInfo.getPaysNaissance())
                .identificationFiscalePaye(request.getIdentificationFiscaleCommercantPaye())
                .latitudeClientPaye(request.getLatitudeClientPaye())
                .longitudeClientPaye(request.getLongitudeClientPaye())
                // Transaction details
                .motif(request.getMotif())
                .typeDocumentReference(request.getTypeDocumentReference())
                .numeroDocumentReference(request.getNumeroDocumentReference())
                .autorisationModificationMontant(request.getAutorisationModificationMontant())
                .statut(RtpStatus.PENDING)
                .build();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Normalise {@code clientDemandeur} to a BCEAO-accepted value. The AIP only
     * accepts {@code "X"} (payé as initiating party) or an {@code https://}
     * URL. Anything else is coerced to {@code "X"} with a warning — the DTO
     * validator already rejects other patterns, this is a last-line guard.
     */
    private static String resolveClientDemandeur(String input) {
        if (input == null || input.isBlank()) return "X";
        String trimmed = input.trim();
        if ("X".equals(trimmed) || trimmed.startsWith("https://")) return trimmed;
        log.warn("clientDemandeur [{}] does not match BCEAO InitgPty rule — coerced to 'X'", trimmed);
        return "X";
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (notBlank(value)) map.put(key, value);
    }

    private RequestToPayResponse toResponse(PiRequestToPay rtp) {
        return RequestToPayResponse.builder()
                .endToEndId(rtp.getEndToEndId())
                .msgId(rtp.getMsgId())
                .identifiantDemandePaiement(rtp.getIdentifiantDemandePaiement())
                .statut(rtp.getStatut())
                .messageDirection(rtp.getDirection())
                .codeRaison(rtp.getCodeRaison())
                .montant(rtp.getMontant())
                .codeMembreParticipantPayeur(rtp.getCodeMembrePayeur())
                .codeMembreParticipantPaye(rtp.getCodeMembrePaye())
                .transferEndToEndId(rtp.getTransferEndToEndId())
                .createdAt(rtp.getCreatedAt() != null ? rtp.getCreatedAt().toString() : null)
                .build();
    }
}
