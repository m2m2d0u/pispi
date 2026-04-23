-- Inbound PACS.008 callbacks from other participants don't always carry
-- every ISO field. BCEAO spec §4.3.1.1 makes both `typeTransaction` (p.69 —
-- mandatory only for paiement programmé) and `canalCommunication` (p.67 —
-- required but has been observed absent on misc callbacks) optional in
-- practice. V14 relaxed the NOT NULL on the name/compte/type-client columns
-- but missed these two — a recent inbound PACS.008 from SNB000 crashed
-- TransferCallbackController with a constraint violation because the sender
-- omitted typeTransaction on a canal-999 transfer.
--
-- The Java side is already null-safe (TransferCallbackController guards both
-- with null checks); this migration aligns the schema with reality.

ALTER TABLE pi_transfer
    ALTER COLUMN type_transaction    DROP NOT NULL,
    ALTER COLUMN canal_communication DROP NOT NULL;
