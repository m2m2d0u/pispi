-- Ajout de adresse_client_payeur et adresse_client_paye sur pi_request_to_pay.
-- Sans ces colonnes, l'INBOUND RTP perd les adresses portées par la PAIN.013
-- d'origine (cf. RequestToPayService.buildPain013Payload qui les inclut via
-- ClientInfo.getAdresse() côté OUTBOUND), ce qui fait que le PACS.008
-- d'acceptation construit dans confirmRtpAcceptance n'a pas d'adresse à
-- copier — d'où la divergence détectée par l'AIP avec
-- "Les données de la demande de paiement et du transfert ne correspondent pas".
--
-- 140 chars : aligné avec ville_client_* (V19) et adresse_client_* sur
-- pi_transfer (V28).

ALTER TABLE pi_request_to_pay
    ADD COLUMN IF NOT EXISTS adresse_client_payeur VARCHAR(140),
    ADD COLUMN IF NOT EXISTS adresse_client_paye   VARCHAR(140);
