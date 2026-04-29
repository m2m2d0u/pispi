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
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.exception.DuplicateRequestException;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ci.sycapay.pispi.util.DateTimeUtil.normaliseIsoInstantMillis;
import static ci.sycapay.pispi.util.DateTimeUtil.nowIso;
import static ci.sycapay.pispi.util.DateTimeUtil.nowIsoPlusSeconds;
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
        Optional<PiRequestToPay> requestToPayOptional = rtpRepository.findByEndToEndIdAndDirection(request.getEndToEndIdSearchPayeur(), MessageDirection.OUTBOUND);
        if (requestToPayOptional.isPresent()) {
            throw new DuplicateRequestException("Une demande de paiment avec le endToEndId existe deja");
        }

        ResolvedClient payeur = clientSearchResolver.resolve(
                request.getEndToEndIdSearchPayeur(), "payeur");
        ResolvedClient paye = clientSearchResolver.resolve(
                request.getEndToEndIdSearchPaye(), "paye");

        validateTypeComptePaye(paye);
        validatePayeRequiredFields(paye);
        validatePayeNaissanceForTypeP(paye);
        validateCanalDateRules(request);
        validateRmtInfStrdConsistency(request);
        validateAutorisationModificationCompatibility(request);
        validatePayeLocationForMerchantCanals(request, paye);
        validateBeneficiaireTypeForCanal(request, paye);
//        validateMandatRequiresVaccAccount(request, paye);

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        // BCEAO spec : "Le participant payé effectue une recherche d'alias du
        // payeur. Le EndToEndId généré au moment de la recherche d'alias est
        // utilisé dans le pain.013." Nous sommes le payé (OUTBOUND PAIN.013) ;
        // le search que NOUS avons fait sur le payeur est référencé par
        // {@code endToEndIdSearchPayeur}. On réutilise SON e2e — pas un neuf —
        // pour respecter le chaînage RAC_SEARCH ↔ pain.013.
        String endToEndId = request.getEndToEndIdSearchPayeur();

        Map<String, Object> pain013 = buildPain013Payload(msgId, endToEndId, request, payeur, paye);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_013,
                MessageDirection.OUTBOUND, pain013, null, null);

        PiRequestToPay rtp = buildEntity(msgId, endToEndId, codeMembre, request, payeur, paye);
        rtpRepository.save(rtp);

        log.info("RTP PAIN.013 emitted: endToEndId={} payeur={} paye={}",
                endToEndId, payeur.codeMembre(), codeMembre);
        log.debug("RTP PAIN.013 payload: {}", objectMapper.writeValueAsString(pain013));
        aipClient.post("/demandes-paiements", pain013);

        return toResponse(rtp);
    }

    public RequestToPayResponse getRtp(String endToEndId) {
        // Direction-agnostic public lookup. With the composite unique on
        // (end_to_end_id, direction) (V42), both legs may legitimately exist
        // locally — return the most recent one. Callers needing direction
        // precision should use the targeted endpoints (/incoming/{id}/...).
        PiRequestToPay rtp = rtpRepository.findFirstByEndToEndIdOrderByIdDesc(endToEndId)
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
        // Rejection of an inbound RTP — we are the débiteur (payeur), the
        // matching local row was created by the PAIN.013 callback (INBOUND).
        PiRequestToPay rtp = rtpRepository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
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
    /**
     * Reject up-front any inconsistent combination of the three Strd
     * "amount lines" — {@code montantAchat}, {@code montantRetrait},
     * {@code fraisRetrait} — before forwarding to the AIP. BCEAO accepts
     * exactly three valid shapes:
     *
     * <ul>
     *   <li><b>Achat simple</b> : aucune des trois lignes Strd. La description
     *       libre passe par {@code motif} (RmtInf/Ustrd).</li>
     *   <li><b>PICASH</b> (retrait seul, sans achat) : {@code montantRetrait}
     *       obligatoire, {@code fraisRetrait} optionnel,
     *       {@code montantAchat} <em>absent</em>.</li>
     *   <li><b>PICO</b> (achat avec cashback) : {@code montantAchat} ET
     *       {@code montantRetrait} obligatoires ensemble,
     *       {@code fraisRetrait} optionnel.</li>
     * </ul>
     *
     * <p>Toute autre combinaison déclenche le rejet AIP <em>"Soit nous avons
     * un retrait dans le cadre d'un achat avec les deux lignes PICO et PI,
     * soit un retrait uniquement avec une ligne PICASH"</em>. On le rattrape
     * ici avec un 400 explicite et un message d'aide pour orienter
     * l'utilisateur vers le bon mode.
     */
    /**
     * Validates {@code autorisationModificationMontant} per spec §4.7.1.1 (p.119).
     *
     * <p>The field has three distinct states with different BCEAO semantics :
     * <ul>
     *   <li><b>absent (null)</b> — paiement immédiat sans débit différé.
     *       Field not emitted on the wire.</li>
     *   <li><b>false</b> — {@code <AmtModAllwd>false} : paiement immédiat avec
     *       débit différé (ex: fin de mois). Valid on ALL canals.
     *       Spec p.120 : when {@code montantRemisePaiementImmediat} is also set
     *       with {@code false}, the AIP treats it as a deferred-debit commission.</li>
     *   <li><b>true</b> — {@code <GrntedPmtReqd>} : facture avec date d'échéance
     *       (guaranteed / variable-amount payment). The XSD requires
     *       {@code <ImdtPmtRbt>} to precede it — only canal {@code 401} (FACTURE)
     *       is confirmed working; all other canals reject with
     *       "Element GrntedPmtReqd: not expected. Expected is ImdtPmtRbt".</li>
     * </ul>
     *
     * <p>This method only validates the {@code true} case. {@code false} and
     * {@code null} pass through unconditionally.
     */
    private static void validateAutorisationModificationCompatibility(RequestToPayRequest req) {
        if (!Boolean.TRUE.equals(req.getAutorisationModificationMontant())) return;
        CanalCommunicationRtp canal = req.getCanalCommunication();

        // false = "débit différé fin de mois" (AmtModAllwd) — forbidden on canal 401.
        // AIP rejects with "Le débit différé n'est pas supporté lorsque le canal c'est '401'".
        if (Boolean.FALSE.equals(req.getAutorisationModificationMontant())) {
            if (canal == CanalCommunicationRtp.FACTURE) {
                throw new IllegalArgumentException(
                        "'autorisationModificationMontant=false' (débit différé fin de mois) "
                                + "n'est pas supporté sur le canal 401 (FACTURE). "
                                + "L'AIP rejette avec 'Le débit différé n'est pas supporté "
                                + "lorsque le canal c'est 401'. "
                                + "Pour un paiement FACTURE à montant variable/garanti, "
                                + "utiliser 'autorisationModificationMontant=true' + remise.");
            }
            return;
        }

        // true = "facture avec date d'échéance" (GrntedPmtReqd) — canal 401 only.
        // AIP rejects other canals with "Element GrntedPmtReqd: not expected. Expected is ImdtPmtRbt".
        if (!Boolean.TRUE.equals(req.getAutorisationModificationMontant())) return;

        if (canal != CanalCommunicationRtp.FACTURE) {
            throw new IllegalArgumentException(
                    "'autorisationModificationMontant=true' (<GrntedPmtReqd>) n'est pas supporté "
                            + "sur le canal " + canal.name() + " (" + canal.getCode() + "). "
                            + "L'AIP rejette avec 'Element GrntedPmtReqd: not expected. "
                            + "Expected is ImdtPmtRbt'. "
                            + "Seul le canal 401 (FACTURE) accepte true (montant variable/garanti). "
                            + "Pour un débit différé sur canal 520, utiliser false + montantRemisePaiementImmediat.");
        }
        // GrntedPmtReqd must be preceded by ImdtPmtRbt in the XSD — remise required.
        boolean hasRemise = req.getMontantRemisePaiementImmediat() != null
                         || req.getTauxRemisePaiementImmediat() != null;
        if (!hasRemise) {
            throw new IllegalArgumentException(
                    "'autorisationModificationMontant=true' nécessite aussi une "
                            + "'montantRemisePaiementImmediat' OU 'tauxRemisePaiementImmediat' "
                            + "(non-null) pour respecter la séquence XSD <ImdtPmtRbt> AVANT "
                            + "<GrntedPmtReqd>. Sans remise, l'AIP rejette le PAIN.013.");
        }
    }

    /**
     * Sur les canaux {@code 500} (MARCHAND_SUR_SITE) et {@code 631}
     * (PARTICULIER), l'AIP exige {@code latitudeClientPaye} et
     * {@code longitudeClientPaye} <em>lorsque le payé est un commerçant</em>
     * (typeClient B / G / C). Sinon il rejette avec :
     *   "La localisation du client payé est obligatoire pour le canal '631'
     *    et le canal '500' lorsque le payé est un commerçant".
     *
     * <p>On rattrape ça en amont pour donner un 400 clair au lieu d'un
     * round-trip AIP cryptique.
     */
    private static void validatePayeLocationForMerchantCanals(
            RequestToPayRequest req, ResolvedClient paye) {
        CanalCommunicationRtp canal = req.getCanalCommunication();
        if (canal != CanalCommunicationRtp.MARCHAND_SUR_SITE
                && canal != CanalCommunicationRtp.PARTICULIER) return;

        TypeClient payeType = paye.clientInfo().getTypeClient();
        boolean isMerchant = payeType == TypeClient.B
                          || payeType == TypeClient.G
                          || payeType == TypeClient.C;
        if (!isMerchant) return;

        boolean hasLat = req.getLatitudeClientPaye() != null
                && !req.getLatitudeClientPaye().isBlank();
        boolean hasLon = req.getLongitudeClientPaye() != null
                && !req.getLongitudeClientPaye().isBlank();
        if (!hasLat || !hasLon) {
            throw new IllegalArgumentException(
                    "Localisation du payé obligatoire : canal " + canal.getCode()
                            + " (" + canal.name() + ") + payé de type " + payeType
                            + " (commerçant) ⟹ 'latitudeClientPaye' ET "
                            + "'longitudeClientPaye' requis dans le corps de la requête. "
                            + "BCEAO rejette autrement avec 'La localisation du client payé "
                            + "est obligatoire pour le canal 631 et le canal 500 lorsque "
                            + "le payé est un commerçant'.");
        }
    }

    /**
     * Sur les canaux {@code 520} (E_COMMERCE_LIVRAISON), {@code 521}
     * (E_COMMERCE_IMMEDIAT) et {@code 401} (FACTURE), le bénéficiaire (payé)
     * ne peut pas être une personne physique ({@code P}) ni un commerçant
     * ({@code C}). Ces canaux sont réservés aux entités de type Business ({@code B})
     * ou Government ({@code G}).
     */
    private static void validateBeneficiaireTypeForCanal(
            RequestToPayRequest req, ResolvedClient paye) {
        CanalCommunicationRtp canal = req.getCanalCommunication();
        if (canal != CanalCommunicationRtp.E_COMMERCE_LIVRAISON
                && canal != CanalCommunicationRtp.E_COMMERCE_IMMEDIAT
                && canal != CanalCommunicationRtp.FACTURE) return;

        TypeClient payeType = paye.clientInfo().getTypeClient();
        if (payeType == TypeClient.P || payeType == TypeClient.C) {
            throw new IllegalArgumentException(
                    "Le bénéficiaire (payé) de type " + payeType + " ("
                            + payeType.getDescription() + ") n'est pas autorisé sur le canal "
                            + canal.name() + " (LclInstrm " + canal.getCode() + "). "
                            + "Les canaux 401, 520 et 521 sont réservés aux bénéficiaires "
                            + "de type B (Business) ou G (Government).");
        }
    }

    /**
     * BCEAO AIP rule: {@code MndtRltdInf} ({@code identifiantMandat} +
     * {@code signatureNumeriqueMandat}) is only accepted when the payé holds a
     * tontine account ({@code typeCompte = VACC}). Sending mandate fields with
     * any other account type triggers ADMI.002 "MndtRltdInf doit être utilisé
     * uniquement dans les demandes de paiement pour les tontines".
     */
    private static void validateMandatRequiresVaccAccount(
            RequestToPayRequest req, ResolvedClient paye) {
        if (!notBlank(req.getIdentifiantMandat())) return;
        if (paye.typeCompte() != TypeCompte.VACC) {
            throw new IllegalArgumentException(
                    "'identifiantMandat' (MndtRltdInf) ne peut être utilisé que pour les "
                            + "paiements de tontine — le compte du payé doit être de type VACC. "
                            + "Type résolu : " + paye.typeCompte() + ". "
                            + "L'AIP rejette autrement avec 'MndtRltdInf doit être utilisé "
                            + "uniquement dans les demandes de paiement pour les tontines'.");
        }
    }

    private static void validateRmtInfStrdConsistency(RequestToPayRequest req) {
        boolean hasAchat   = req.getMontantAchat() != null;
        boolean hasRetrait = req.getMontantRetrait() != null;
        boolean hasFrais   = req.getFraisRetrait() != null;

        // Cas 1 : aucune des trois → achat simple, OK (Strd vide, Ustrd seul)
        if (!hasAchat && !hasRetrait && !hasFrais) return;

        // Garde canal-aware : PICO / PICASH (signalés par la présence de
        // montantRetrait ou fraisRetrait — montantAchat seul est traité plus
        // bas comme combinaison invalide indépendamment du canal) ne sont
        // autorisés que sur le canal 500 (MARCHAND_SUR_SITE). L'AIP rejette
        // autrement avec "Le local instrument doit être 500 lorsqu'il s'agit
        // de PICO ou PICASH" (ADMI.002 sur les canaux 631 / 521 / 520 / 401).
        if ((hasRetrait || hasFrais)
                && req.getCanalCommunication() != CanalCommunicationRtp.MARCHAND_SUR_SITE) {
            throw new IllegalArgumentException(
                    "PICO / PICASH ('montantRetrait' ou 'fraisRetrait' présent) ne sont "
                            + "supportés que sur le canal MARCHAND_SUR_SITE (500). Canal "
                            + "actuel : " + req.getCanalCommunication().name() + " ("
                            + req.getCanalCommunication().getCode() + "). "
                            + "L'AIP rejette autrement avec 'Le local instrument doit être "
                            + "500 lorsqu'il s'agit de PICO ou PICASH'. Pour un transfert "
                            + "P2P (canal 631) ou e-commerce (520/521), retirer "
                            + "'montantRetrait' et 'fraisRetrait' — la description passe par "
                            + "'motif' (Ustrd).");
        }

        // Cas 2 : PICASH (retrait seul) — montantRetrait obligatoire, montantAchat absent
        if (!hasAchat && hasRetrait) {
            // BCEAO rule: montant must equal montantRetrait (les frais sont
            // débités séparément via fraisRetrait, mais ne sont PAS inclus
            // dans le champ 'montant' du PAIN.013).
            if (req.getMontant() != null
                    && req.getMontant().compareTo(req.getMontantRetrait()) != 0) {
                throw new IllegalArgumentException(
                        "PICASH : 'montant' (" + req.getMontant() + ") doit être égal à "
                                + "'montantRetrait' (" + req.getMontantRetrait() + "). "
                                + "Les frais ('fraisRetrait') sont débités séparément du compte "
                                + "client, ils ne sont PAS inclus dans 'montant'. "
                                + "BCEAO rejette autrement avec 'Quand c'est PICASH, le montant "
                                + "du retrait doit etre egale au montant de la demande de paiement'.");
            }
            return;
        }

        // Cas 3 : PICO (achat + cashback) — les DEUX présents
        if (hasAchat && hasRetrait) {
            // BCEAO rule (par symétrie avec PICASH): montant = montantAchat + montantRetrait
            // (les frais éventuels sont débités séparément).
            BigDecimal expected = req.getMontantAchat().add(req.getMontantRetrait());
            if (req.getMontant() != null
                    && req.getMontant().compareTo(expected) != 0) {
                throw new IllegalArgumentException(
                        "PICO : 'montant' (" + req.getMontant() + ") doit être égal à "
                                + "'montantAchat' + 'montantRetrait' (" + expected + "). "
                                + "Les frais ('fraisRetrait') sont débités séparément.");
            }
            return;
        }

        // Tout le reste est invalide.
        if (hasAchat && !hasRetrait) {
            throw new IllegalArgumentException(
                    "Combinaison Strd invalide : 'montantAchat' seul n'est pas accepté par "
                            + "BCEAO (le validateur AIP rejette avec 'Soit nous avons un retrait "
                            + "dans le cadre d'un achat avec les deux lignes PICO et PI, soit un "
                            + "retrait uniquement avec une ligne PICASH'). "
                            + "Trois options : (a) retirer 'montantAchat' pour un achat simple "
                            + "(la description passe par 'motif') ; (b) ajouter 'montantRetrait' "
                            + "pour un PICO (achat + cashback) ; (c) si vous voulez juste signaler "
                            + "un débit différé, utiliser 'autorisationModificationMontant: true' "
                            + "à la place.");
        }
        if (hasFrais && !hasRetrait) {
            throw new IllegalArgumentException(
                    "Combinaison Strd invalide : 'fraisRetrait' suppose un retrait — "
                            + "'montantRetrait' est obligatoire pour PICASH ou PICO. "
                            + "Ajouter 'montantRetrait' ou retirer 'fraisRetrait'.");
        }
    }

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

    /**
     * BCEAO §4.7.1.1 (p.114–115) : pour un payé de type P (personne physique),
     * {@code dateNaissanceClientPaye}, {@code villeNaissanceClientPaye} et
     * {@code paysNaissanceClientPaye} sont conditionnellement obligatoires
     * ({@code <DtAndPlcOfBirth>} dans le PAIN.013). L'AIP rejette en ADMI.002
     * si ces champs sont absents pour un type P. On rattrape en amont.
     */
    private static void validatePayeNaissanceForTypeP(ResolvedClient paye) {
        if (paye.clientInfo().getTypeClient() != TypeClient.P) return;
        ClientInfo info = paye.clientInfo();
        if (!notBlank(info.getDateNaissance())) {
            throw new IllegalArgumentException(
                    "Le payé est de type P (personne physique) mais son alias ne porte pas de "
                            + "'dateNaissance' (dateNaissanceClientPaye). BCEAO DemandePaiement "
                            + "§4.7.1.1 exige <DtAndPlcOfBirth><BirthDt> pour les personnes physiques. "
                            + "Compléter l'alias du bénéficiaire ou choisir un autre alias.");
        }
        if (!notBlank(info.getLieuNaissance())) {
            throw new IllegalArgumentException(
                    "Le payé est de type P (personne physique) mais son alias ne porte pas de "
                            + "'lieuNaissance' (villeNaissanceClientPaye). BCEAO DemandePaiement "
                            + "§4.7.1.1 exige <DtAndPlcOfBirth><CityOfBirth> pour les personnes physiques. "
                            + "Compléter l'alias du bénéficiaire ou choisir un autre alias.");
        }
        if (!notBlank(info.getPaysNaissance())) {
            throw new IllegalArgumentException(
                    "Le payé est de type P (personne physique) mais son alias ne porte pas de "
                            + "'paysNaissance' (paysNaissanceClientPaye). BCEAO DemandePaiement "
                            + "§4.7.1.1 exige <DtAndPlcOfBirth><CtryOfBirth> pour les personnes physiques. "
                            + "Compléter l'alias du bénéficiaire ou choisir un autre alias.");
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
        //
        // Canal 401 (FACTURE) special-case: spec §4.7.1.1 declares dateHeureExecution
        // mandatory, but the AIP enforces TWO contradictory rules:
        //   1. "Le débit différé n'est pas supporté lorsque le canal c'est '401'"
        //      → no significantly-future value
        //   2. "ReqdExctnDt doit être supérieure ou égale à GrpHdr.CreDtTm"
        //      → must be >= the message creation timestamp the AIP inserts on
        //      its end (which is later than our nowIso() by network latency)
        // The reconciliation: send {@code now + small offset (30s)}. It's late
        // enough to stay >= CreDtTm even with clock skew & RTT, early enough
        // to still read as "immediate" (not "deferred"). Caller-supplied
        // values (likely future invoice due dates) are logged and overridden.
        if (request.getCanalCommunication() == CanalCommunicationRtp.FACTURE) {
            String supplied = request.getDateHeureExecution();
            String forced = nowIsoPlusSeconds(30);
            p.put("dateHeureExecution", forced);
            if (notBlank(supplied) && !forced.equals(supplied)) {
                log.info("Canal 401: dateHeureExecution overridden to now+30s ({} → {}) "
                                + "— AIP refuses both 'débit différé' (future date) and "
                                + "'ReqdExctnDt < CreDtTm' (now), so we use a small forward "
                                + "offset that satisfies both",
                        supplied, forced);
            }
        } else {
            putIfNotBlank(p, "dateHeureExecution",
                    normaliseIsoInstantMillis(request.getDateHeureExecution()));
        }
        putIfNotBlank(p, "dateHeureLimiteAction",
                normaliseIsoInstantMillis(request.getDateHeureLimiteAction()));
        // Canal & monetary (BCEAO encodes numbers/booleans as strings).
        p.put("canalCommunication", request.getCanalCommunication().getCode());
        p.put("montant", request.getMontant().toBigInteger().toString());
        // Spec §4.7.1.1 (p.119) distinguishes two explicit values:
        //   true  → <GrntedPmtReqd>  "facture avec date d'échéance" (variable/guaranteed amount)
        //           XSD requires <ImdtPmtRbt> to precede it — only canal 401 confirmed working.
        //   false → <AmtModAllwd>false "paiement immédiat avec débit différé" (end-of-month debit)
        //           Different XML element, no XSD sequence dependency.
        // Absent (null) → field omitted = pure immediate payment without deferred semantics.
        // Both true and false must be serialised when explicitly set by the caller.
        if (Boolean.TRUE.equals(request.getAutorisationModificationMontant())) {
            p.put("autorisationModificationMontant", "true");
        } else if (Boolean.FALSE.equals(request.getAutorisationModificationMontant())) {
            p.put("autorisationModificationMontant", "false");
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
        // identificationFiscaleCommercant maps to <Dbtr>/<Id>/<OrgId>/<Othr> —
        // the SAME XSD path as numeroIdentificationClientPayeur. BCEAO restricted
        // schema caps Othr at maxOccurs=1 inside OrgId, so emitting both for a
        // type B/G client triggers "Element 'Othr': not expected" (we hit this
        // on PACS.008 and fixed it in ClientSearchResolver — same rule applies
        // to PAIN.013). For B/G the fiscal IS the primary numeroIdentification
        // (TXID), so skip the duplicate; only type C carries a distinct
        // commercial fiscal ID alongside its NIDN primary.
        TypeClient payeurTypeClient = payeurInfo.getTypeClient();
        boolean payeurIsBG = payeurTypeClient == TypeClient.B || payeurTypeClient == TypeClient.G;
        if (!payeurIsBG) {
            String idFiscCommPayeur = payeur.identificationFiscaleCommercant() != null
                    ? payeur.identificationFiscaleCommercant()
                    : request.getIdentificationFiscaleCommercantPayeur();
            if (idFiscCommPayeur != null) {
                p.put("identificationFiscaleCommercantPayeur", idFiscCommPayeur);
            } else if (payeur.identificationRccm() != null) {
                p.put("numeroRCCMClientPayeur", payeur.identificationRccm());
            }
        } else if (payeur.identificationRccm() != null
                && payeurInfo.getIdentifiant() == null) {
            // B/G with RCCM only (no fiscal) — RCCM becomes the sole identifier
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
        // Same dedup as payeur: skip identificationFiscaleCommercant for B/G
        // (numeroIdentification already carries it) — see header comment above.
        TypeClient payeTypeClient = payeInfo.getTypeClient();
        boolean payeIsBG = payeTypeClient == TypeClient.B || payeTypeClient == TypeClient.G;
        if (!payeIsBG) {
            String idFiscCommPaye = paye.identificationFiscaleCommercant() != null
                    ? paye.identificationFiscaleCommercant()
                    : request.getIdentificationFiscaleCommercantPaye();
            if (idFiscCommPaye != null) {
                p.put("identificationFiscaleCommercantPaye", idFiscCommPaye);
            } else if (paye.identificationRccm() != null) {
                p.put("numeroRCCMClientPaye", paye.identificationRccm());
            }
        } else if (paye.identificationRccm() != null
                && payeInfo.getIdentifiant() == null) {
            p.put("numeroRCCMClientPaye", paye.identificationRccm());
        }

        // -----------------------------------------------------------------
        // RmtInf mutual exclusion — BCEAO pain.013 XSD accepts EITHER
        //   RmtInf/Ustrd   (filled by motif)
        //   RmtInf/Strd    (filled by montantRetrait, fraisRetrait,
        //                   montantAchat, typeDocumentReference,
        //                   numeroDocumentReference, referenceBulk)
        // …never both. Although spec §4.7.1.1 (p.118) maps referenceBulk to
        // <PmtId><InstrId>, the AIP transformer routes it into RmtInf/Strd,
        // so it must follow the same Ustrd/Strd mutual exclusion rule as the
        // other Strd fields — motif is dropped when referenceBulk is present.
        // -----------------------------------------------------------------
        boolean hasStrd =
                request.getMontantRetrait() != null
                || request.getFraisRetrait() != null
                || request.getMontantAchat() != null
                || request.getTypeDocumentReference() != null
                || notBlank(request.getNumeroDocumentReference())
                || notBlank(request.getReferenceBulk());

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
            putIfNotBlank(p, "referenceBulk", request.getReferenceBulk());
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
                .adresseClientPayeur(payeurInfo.getAdresse())
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
                .adresseClientPaye(payeInfo.getAdresse())
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
                .detailEchec(rtp.getDetailEchec())
                .montant(rtp.getMontant())
                .codeMembreParticipantPayeur(rtp.getCodeMembrePayeur())
                .codeMembreParticipantPaye(rtp.getCodeMembrePaye())
                .transferEndToEndId(rtp.getTransferEndToEndId())
                .createdAt(rtp.getCreatedAt() != null ? rtp.getCreatedAt().toString() : null)
                .build();
    }
}
