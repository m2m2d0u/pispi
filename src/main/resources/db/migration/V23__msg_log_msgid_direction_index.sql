-- Replace the single-column UNIQUE index on pi_message_log(msg_id) with a
-- composite UNIQUE index on (msg_id, direction).
--
-- Rationale: the BCEAO ISO 20022 msgId is globally unique per message, but
-- when the PI-SPI logs the same business message from both sides (rare but
-- possible — an outbound send and a callback that echoes the original
-- msgId), the old unique-on-msg_id constraint blocks the second write. The
-- composite form keeps the integrity guarantee per side while allowing the
-- edge case. A query filtered by msg_id alone still benefits from this
-- composite index because PostgreSQL can range-scan on the leading column.

DROP INDEX IF EXISTS idx_msg_log_msg_id;

CREATE UNIQUE INDEX idx_msg_log_msgid_direction
    ON pi_message_log (msg_id, direction);
