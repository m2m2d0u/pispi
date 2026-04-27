-- V31 dropped date_heure_execution from pi_request_to_pay, but the running
-- application still maps PiRequestToPay.dateHeureExecution to this column.
-- Restore it as nullable until the entity field is removed and redeployed.
ALTER TABLE pi_request_to_pay
    ADD COLUMN IF NOT EXISTS date_heure_execution TIMESTAMP;
