-- V3: Make msg_id nullable in pi_message_log
--
-- Outbound messages (RAC_CREATE, RAC_MODIFY, RAC_DELETE, RAC_SEARCH, etc.)
-- are logged before the AIP assigns a msgId. Only inbound callbacks carry a msgId.
-- The end_to_end_id already provides full traceability for outbound entries.
-- The unique index on msg_id remains valid for non-null values (PostgreSQL
-- treats each NULL as distinct, so multiple NULL rows are allowed).

ALTER TABLE pi_message_log ALTER COLUMN msg_id DROP NOT NULL;
