-- Increase end_to_end_id length to accommodate UUID with prefix (e.g., MOD_uuid, DEL_uuid)
ALTER TABLE pi_message_log ALTER COLUMN end_to_end_id TYPE VARCHAR(50);
