-- BCEAO AIP rejects PACS.008 for B/G/C clients without villeClient*:
--   "La ville est obligatoire pour les personnes morales (B, G) et les
--    commerçants (C)"
--
-- Spec §4.3.1.1 (p.67-68 of BCEAO-Doc.pdf):
--   villeClientPayeur → <Dbtr><PstlAdr><TwnNm>, 1-35 chars, conditional
--     (required when typeClient in B/G or when latitude+longitude are
--      provided — AIP practice extends to C too)
--   villeClientPaye   → <Cdtr><PstlAdr><TwnNm>, 1-35 chars, conditional
--   adresseClientPayeur → <Dbtr><PstlAdr><AdrLine>, 1-140 chars, optional
--   adresseClientPaye   → <Cdtr><PstlAdr><AdrLine>, 1-140 chars, optional
--
-- Both values flow from the RAC_SEARCH payload through ClientInfo — we
-- were just missing the snapshot columns to persist them on pi_transfer.

ALTER TABLE pi_transfer
    ADD COLUMN ville_client_payeur       VARCHAR(35),
    ADD COLUMN ville_client_paye         VARCHAR(35),
    ADD COLUMN adresse_client_payeur     VARCHAR(140),
    ADD COLUMN adresse_client_paye       VARCHAR(140);
