-- After the legacy TransferService was removed, six pi_transfer columns no
-- longer have any live writers in the codebase and can be safely dropped:
--
--   reference_client   — was populated from TransferRequest.identifiantTransaction
--                        (field dropped from the mobile DTO)
--   montant_achat      — legacy PICASH "montant d'achat" field; RTP has its own
--                        column on pi_request_to_pay and pacs.008 doesn't carry it
--   montant_retrait    — same as above — PICASH retrait
--   frais_retrait      — same as above — PICASH retrait fees
--   rtp_end_to_end_id  — old linkage column used by the legacy flow to tie a
--                        PACS.008 back to the originating RTP; no reader/writer
--                        left after the TransferService removal
--   detail_echec       — declared in V17 but never written on pi_transfer.
--                        Admi002CallbackService reads detailEchec from the admi.002
--                        payload and fires a webhook — it does not persist it
--                        onto the transfer row.
--
-- The corresponding partial index idx_transfer_rtp is dropped with its column.

DROP INDEX IF EXISTS idx_transfer_rtp;

ALTER TABLE pi_transfer
    DROP COLUMN IF EXISTS reference_client,
    DROP COLUMN IF EXISTS montant_achat,
    DROP COLUMN IF EXISTS montant_retrait,
    DROP COLUMN IF EXISTS frais_retrait,
    DROP COLUMN IF EXISTS rtp_end_to_end_id,
    DROP COLUMN IF EXISTS detail_echec;
