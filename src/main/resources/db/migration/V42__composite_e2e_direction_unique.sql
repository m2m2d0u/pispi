-- Composite unique constraint (end_to_end_id, direction) on pi_transfer,
-- pi_request_to_pay and pi_identity_verification.
--
-- The single-column unique on end_to_end_id is too strict for two scenarios :
--   1) Self-loop sandbox : we emit a PAIN.013/PACS.008/ACMT.023 OUTBOUND, the
--      AIP routes it back to us as INBOUND because codeMembrePaye matches our
--      own participant — both rows must coexist with the same endToEndId.
--   2) Multi-tenant deployment : two participants gérés par cette même
--      plateforme transactent entre eux ; le row OUTBOUND du PI émetteur et
--      le row INBOUND du PI récepteur partagent le endToEndId généré par
--      l'émetteur.
--
-- (end_to_end_id, direction) keeps idempotence within a direction (a retried
-- inbound delivery with the same endToEndId is still a unique-violation, as
-- desired) while allowing the OUTBOUND/INBOUND pair to coexist.

DROP INDEX IF EXISTS idx_transfer_e2e;
CREATE UNIQUE INDEX idx_transfer_e2e ON pi_transfer (end_to_end_id, direction);

DROP INDEX IF EXISTS idx_rtp_e2e;
CREATE UNIQUE INDEX idx_rtp_e2e ON pi_request_to_pay (end_to_end_id, direction);

DROP INDEX IF EXISTS idx_verif_e2e;
CREATE UNIQUE INDEX idx_verif_e2e ON pi_identity_verification (end_to_end_id, direction);
