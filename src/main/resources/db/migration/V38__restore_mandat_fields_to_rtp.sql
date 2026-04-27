-- identifiant_mandat and signature_numerique_mandat were dropped by V31.
-- They are required for tontine (recurring mandate) PAIN.013 flows
-- where the creditor attaches a mandate reference and its digital signature.
ALTER TABLE pi_request_to_pay
    ADD COLUMN IF NOT EXISTS identifiant_mandat          VARCHAR(140),
    ADD COLUMN IF NOT EXISTS signature_numerique_mandat  TEXT;
