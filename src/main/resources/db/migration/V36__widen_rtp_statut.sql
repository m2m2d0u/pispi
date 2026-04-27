-- V2 set pi_request_to_pay.statut to VARCHAR(10), which fitted the original
-- enum values (PENDING=7, ACCEPTED=8, RJCT=4, EXPIRED=7, TMOT=4).
-- The new PREVALIDATION value (13 chars) overflows that limit.
ALTER TABLE pi_request_to_pay
    ALTER COLUMN statut TYPE VARCHAR(20);
