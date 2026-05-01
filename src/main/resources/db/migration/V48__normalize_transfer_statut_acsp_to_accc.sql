-- =============================================================================
-- V48 — Uniformisation des transferts réussis : ACSP → ACCC
-- =============================================================================
--
-- Contexte : la spec BCEAO impose que le PACS.002 OUTBOUND porte
-- statutTransaction=ACSP (Accepted Settlement in Process) sur l'acceptation
-- d'un PACS.008 entrant. L'AIP est censée renvoyer ensuite un PACS.002
-- INBOUND ACCC|ACSC une fois la compensation finalisée — mais en pratique,
-- certaines AIP (notamment sur les flux internes participant) ne renvoient
-- jamais cette finalisation, et la ligne reste bloquée en ACSP côté local.
--
-- Le code applicatif normalise désormais ACSP → ACCC lors du stockage
-- (cf. TransferStatus.normalizeSuccess). Cette migration applique
-- rétroactivement la même normalisation aux lignes existantes pour
-- garantir un état uniforme : tout transfer accepté = ACCC.
--
-- Le payload OUTBOUND vers l'AIP continue à utiliser ACSP explicitement
-- (BCEAO l'impose) — seul le miroir local en base est uniformisé.
-- =============================================================================

UPDATE pi_transfer
SET    statut = 'ACCC'
WHERE  statut = 'ACSP';

-- Note : on ne migre pas pi_message_log, qui contient les payloads ISO 20022
-- bruts (input/output AIP) et doit conserver ACSP dans le statutTransaction
-- pour traçabilité. Seul l'état dérivé pi_transfer.statut est uniformisé.
