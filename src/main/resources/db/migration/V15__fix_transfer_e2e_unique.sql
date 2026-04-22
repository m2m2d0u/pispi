-- The unique constraint on end_to_end_id alone is too strict:
-- for DISP transfers the same endToEndId exists as both OUTBOUND (sent) and INBOUND (callback).
-- Replace with a composite unique on (end_to_end_id, direction).
DROP INDEX IF EXISTS idx_transfer_e2e;
CREATE UNIQUE INDEX idx_transfer_e2e ON pi_transfer (end_to_end_id, direction);
