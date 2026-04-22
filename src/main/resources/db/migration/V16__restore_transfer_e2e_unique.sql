-- Inbound PACS.008 echoes of our own outbound transfers are no longer inserted as separate rows.
-- Restore the single-column unique constraint on end_to_end_id.
DROP INDEX IF EXISTS idx_transfer_e2e;
CREATE UNIQUE INDEX idx_transfer_e2e ON pi_transfer (end_to_end_id);
