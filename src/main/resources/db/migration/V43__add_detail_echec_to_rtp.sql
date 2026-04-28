-- Add detail_echec to pi_request_to_pay (parity with pi_transfer V39 and
-- pi_identity_verification V40). The ADMI.002 / PAIN.014 callbacks receive a
-- free-text rejection detail from the AIP that doesn't fit the strict
-- codeRaison enum — we want to keep it on the local row for diagnostics.

ALTER TABLE pi_request_to_pay ADD COLUMN detail_echec VARCHAR(500);
