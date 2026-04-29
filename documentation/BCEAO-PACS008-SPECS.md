# BCEAO PI — Spécifications du message ISO 20022: pacs.008.001.10

> Source: BCEAO-PI-SPECS-MESSAGES-PACS008.pdf — version 4.0.5
> Schéma des données échangées entre le participant et l'application d'interfaçage (AIP)

---

## Vue d'ensemble

Le message **pacs.008** est envoyé par un participant **Payeur** à destination d'un participant **Payé** pour un transfert de fond ou un paiement via le système interopérable.

### Cas d'utilisation

| Type | Description |
|------|-------------|
| **Virement** | Transfert de fonds en faveur d'un client de type P, tant que le volume ne dépasse pas 30 opérations par mois calendaire |
| **Paiement** | Transfert de fonds dont le bénéficiaire est un client de type B, C, G, ou un client de type P dont le volume dépasse 30 opérations par mois |
| **Suite à un RTP** | Le paiement peut faire suite à une demande de paiement (PAIN.013) initiée par le payé |
| **Paiement de masse (Bulk)** | Transferts entre un payeur et plusieurs payés de manière unitaire — le système PI traite chaque virement avec une indication de groupe |

### Flux de traitement

**Cas standard (virement accepté) :**
```
Participant Payeur → (1) PACS.008 → SWITCH → (2) PACS.008 → Participant Payé
                                              ← (3) PACS.002 ←
Participant Payeur ← (4) PACS.002 ← SWITCH
```

**Cas bénéficiaire B/C/G (2 PACS.002) :**
```
Participant Payeur → PACS.008 → SWITCH → Participant Payé
                  ← PACS.002 (2b) ←      → PACS.002 (2a) →
```
> Lorsque le bénéficiaire est de type B, C ou G, le participant payé retourne un PACS.002 vers le SWITCH, qui en retourne un autre vers le participant payeur.

---

## Modèle de données — Champs du pacs.008

### Niveau GroupHeader (`<GrpHdr>`)

| Champ ISO | Balise XML | Card. PI | Type | Description | Règle de gestion |
|-----------|-----------|----------|------|-------------|-----------------|
| MessageIdentification | `<MsgId>` | [1,1] | Max35Text | Identifiant unique du message | Format: **M** + CodeMembre(6) + UNIQUEID(28) |
| CreationDateTime | `<CreDtTm>` | [1,1] | ISODateTime | Date et heure de création du message | La date doit être proche de l'heure de performance du système |
| NumberOfTransactions | `<NbOfTxs>` | [1,1] | Max15NumericText | Nombre de transactions contenu dans le message | Le compte de transactions dans le message doit être égal à 1 à la plateforme PI |
| SettlementInformation | `<SttlmInf>` | [1,1] | — | Méthode de règlement des transactions entre membres | — |
| SettlementMethod | `<SttlmMtd>` | [1,1] | CodeSet | Méthode de règlement des transactions entre membres | La méthode de règlement des transactions doit être `CLRG` |

### Niveau CreditTransferTransactionInformation (`<CdtTrfTxInf>`)

#### Identification

| Champ ISO | Balise XML | Card. PI | Type | Description | Règle de gestion |
|-----------|-----------|----------|------|-------------|-----------------|
| PaymentIdentification | `<PmtId>` | [1,1] | — | Identification du paiement | — |
| InstructionIdentification | `<InstrId>` | [0,1] | Max35Text | Identifiant unique attribué par le participant payeur pour identifier un groupe de transactions | — |
| EndToEndIdentification | `<EndToEndId>` | [1,1] | Max35Text | Identifiant unique attribué par la partie initiatrice | Format: **E** + CodeMembre(6) + Date(14) + UNIQUEID(14). Doit contenir le code membre et `UUID-` pour les QR codes. |
| TransactionIdentification | `<TxId>` | [0,1] | Max35Text | Identifiant unique, attribué par le premier donneur d'ordre — pour chaque transfert, tout au long de la chaîne de traitement | **Obligatoire** pour les paiements par QR Code Dynamique (canal 400). Champ permettant aussi de modifier le montant ou les frais d'une transaction, dans le cas des canaux de paiement par RTP. |

#### Payment Type Information

| Champ ISO | Balise XML | Card. PI | Type | Description | Règle de gestion |
|-----------|-----------|----------|------|-------------|-----------------|
| ServiceLevel | `<SvcLvl>` | [0,1] | — | Accord dans le cadre duquel la transaction est traitée | — |
| Proprietary | `<Prtry>` | [1,1] | Max35Text | Canal de communication | Canal de communication comme sous forme de code propriétaire |
| LocalInstrument | `<LclInstrm>` | [0,1] | — | — | — |
| Proprietary | `<Prtry>` | [1,1] | Max35Text | Canal de communication | — |

#### Montant et règlement

| Champ ISO | Balise XML | Card. PI | Type | Description | Règle de gestion |
|-----------|-----------|----------|------|-------------|-----------------|
| InterbankSettlementAmount | `<IntrBkSttlmAmt>` | [1,1] | Amount | Montant du règlement interbancaire | Montant en XOF |
| AcceptanceDateTime | `<AccptncDtTm>` | [1,1] | DateTime | Date de confirmation de transfert ou du paiement part le client | Doit être la date avant les contrôles sauf pour l'ordre de transfert bancaire |
| ChargeBearer | `<ChrgBr>` | [1,1] | CodeSet | Indique quelle partie prend en charge les frais | Valeur: `SLEV` |

#### Débiteur (Payeur)

| Champ JSON | Balise XML | Card. PI | Type | Description | Règle de gestion |
|------------|-----------|----------|------|-------------|-----------------|
| `nomClientPayeur` | `<Dbtr>/<Nm>` | [1,1] | Max140Text | Nom du client payeur | — |
| `paysClientPayeur` | `<Dbtr>/<PstlAdr>/<CtryOfRes>` | [1,1] | CountryCode | Pays de résidence du client payeur | — |
| `villeClientPayeur` | `<Dbtr>/<PstlAdr>/<TwnNm>` | [0,1] | Max35Text | Nom de la ville du client payeur | Non de la ville du client payeur pour les clients type Business (B), Gouvernement (G) et Commerçant (C) avec POID |
| `adresseClientPayeur` | `<Dbtr>/<PstlAdr>/<AdrLine>` | [0,1] | Max70Text | Adresse en degrés décimaux (latitude) | — |
| (latitude) | `<Dbtr>/<PstlAdr>/<AdrLine>` | [0,1] | Max70Text | Longitude en degrés décimaux | — |
| `typeClientPayeur` | `<Dbtr>/<Id>` | [0,1] | — | Information ou critère identifiant une personne morale ou physique | — |
| `systemeIdentificationClientPayeur` + `numeroIdentificationClientPayeur` | `<Dbtr>/<Id>/<OrgId>/<Othr>` (B/G) ou `<Dbtr>/<Id>/<PrvtId>/<Othr>` (P/C) | [0,1] | — | Identification du client | Pour B/G: `<Cd>TXID</Cd>` via `<OrgId>`. Pour P: `<Cd>NIDN/CCPT</Cd>` via `<PrvtId>`. Pour C: RCCM sans `<Cd>`. |
| `identificationFiscaleCommercantPayeur` | (voir note) | [0,1] | Max35Text | Identification fiscale du commerçant payeur | Pour B/G, éviter d'émettre simultanément avec `systemeIdentification` — double `<Othr>` interdit |
| `numeroRCCMClientPayeur` | `<Dbtr>/<Id>/<PrvtId>/<Othr>/<Id>` | [0,1] | Max35Text | Numéro RCCM (sans `<SchmeNm>`) | Pour type C — pas de `<Cd>` pour éviter l'erreur POID |
| `typeCompteClientPayeur` | `<DbtrAcct>/<Tp>` | [0,1] | CodeSet | Type de compte avec KYC | Valeur du champ doit être unique devise XOF |
| `deviseCompteClientPayeur` | `<DbtrAcct>/<Ccy>` | [0,1] | Code | Devise | Toujours `XOF` |
| `ibanClientPayeur` / `otherClientPayeur` | `<DbtrAcct>/<Id>` | [1,1] | Max34Text | Information bancaire du compte du débiteur | IBAN pour CACC/SVGS/LLSV/VACC/TAXE. `<Othr>` pour TRAN/TRAL. |
| `aliasClientPayeur` | `<DbtrAcct>/<Prxy>` | [0,1] | Max2048Text | Alias de compte utilisé | UUID ou numéro de téléphone |

#### Agent débiteur et créditeur

| Champ JSON | Balise XML | Card. PI | Type | Description |
|------------|-----------|----------|------|-------------|
| `codeMembreParticipantPayeur` | `<DbtrAgt>/<FinInstnId>/<Othr>/<Id>` | [1,1] | Max35Text | Code Membre PI du participant payeur |
| `codeMembreParticipantPaye` | `<CdtrAgt>/<FinInstnId>/<Othr>/<Id>` | [1,1] | Max35Text | Code Membre PI du participant payé |

#### Créditeur (Payé)

| Champ JSON | Balise XML | Card. PI | Type | Description | Règle de gestion |
|------------|-----------|----------|------|-------------|-----------------|
| `nomClientPaye` | `<Cdtr>/<Nm>` | [1,1] | Max140Text | Nom du client payé | — |
| `paysClientPaye` | `<Cdtr>/<PstlAdr>/<CtryOfRes>` | [1,1] | CountryCode | Pays de résidence | — |
| `villeClientPaye` | `<Cdtr>/<PstlAdr>/<TwnNm>` | [0,1] | Max35Text | Ville du client payé | — |
| `adresseClientPaye` | `<Cdtr>/<PstlAdr>/<AdrLine>` | [0,1] | Max70Text | Adresse / latitude | — |
| `typeClientPaye` | `<Cdtr>/<Id>` | [0,1] | — | Identification | B/G: `<OrgId>`. P/C: `<PrvtId>` |
| `systemeIdentificationClientPaye` + `numeroIdentificationClientPaye` | `<Cdtr>/<Id>/<PrvtId>/<Othr>` | [0,2] | — | Identification personnelle | Pour P: NIDN ou CCPT. Pour C: **ne pas utiliser** (RCCM à la place) |
| `numeroRCCMClientPaye` | `<Cdtr>/<Id>/<PrvtId>/<Othr>/<Id>` | [0,1] | Max35Text | RCCM (type C) — sans `<SchmeNm>/<Cd>` | Évite l'erreur XSD POID |
| `ibanClientPaye` / `otherClientPaye` | `<CdtrAcct>/<Id>` | [1,1] | Max34Text | Identification du compte créditeur | IBAN ou `<Othr>` |
| `typeCompteClientPaye` | `<CdtrAcct>/<Tp>` | [0,1] | CodeSet | Type de compte | — |
| `aliasClientPaye` | `<CdtrAcct>/<Prxy>` | [0,1] | Max2048Text | Alias de compte utilisé | — |

#### RemittanceInformation

> **Exclusion mutuelle :** `Ustrd` (motif libre) et `Strd` (informations structurées) sont mutuellement exclusifs dans le bloc `RmtInf`.

| Champ JSON | Balise XML | Card. PI | Type | Description |
|------------|-----------|----------|------|-------------|
| `motif` | `<RmtInf>/<Ustrd>` | [0,1] | Max140Text | Informations pour le bénéficiaire avec un document ou raison de paiement |
| `typeDocumentReference` + `numeroDocumentReference` | `<RmtInf>/<Strd>/<RfrdDocInf>` | [0,4] | — | Identifiant et le contexte du document spécifié dans le bloc `Structured` |
| `montantAchat` | `<RmtInf>/<Strd>/<RfrdDocAmt>/<RmtdAmt>` | [0,1] | Amount | Montant d'achat |
| `montantRetrait` | `<RmtInf>/<Strd>/<AddtlRmtInf>` (PICASH) | [0,1] | Amount | Montant retrait |
| `fraisRetrait` | `<RmtInf>/<Strd>/<AddtlRmtInf>` (PICO) | [0,1] | Amount | Frais retrait |
| `referenceBulk` | `<RmtInf>/<Strd>` | [0,1] | Max35Text | Référence bulk (paiements de masse) |

---

## Codes de référence

### ExternalCashAccountType1Code (TypeCompte)

| Code | Nom | Description |
|------|-----|-------------|
| `CACC` | Courant | Compte utilisé pour enregistrer des débits et crédits lorsqu'aucun compte spécifique n'a été désigné |
| `SVGS` | Epargne | Compte utilisé pour l'épargne |
| `LLSV` | Tirelire | Compte ouvert dans les livres de la BCEAO pour le trésor public |
| `TAXE` | Taxe | Compte ouvert dans les livres de la BCEAO pour le trésor public |
| `TRAN` | Compte de transaction | Un compte de paiement, ou un compte de monnaie électronique ou un compte de SFD |
| `VACC` | Compte de tontine | Compte virtuel utilisé pour les tontines |

### PITypeCompte (sans KYC)

| Code | Nom | Description |
|------|-----|-------------|
| `TRAL` | Compte de transaction sans KYC | Compte sans KYC avec un plafond de 200 000 Francs CFA. Non utilisable par les établissements de paiements |

### PICanauxCommunication

| Code | Canal |
|------|-------|
| `731` | Envoi par QR Code |
| `633` | Envoi par adresse de paiement / Envoi par alias numéro de téléphone / Envoi par compte |
| `999` | Ordre de transfert bancaire |
| `000` | Paiement par QR Code Statique |
| `400` | Paiement par QR Code Dynamique |
| `733` | Ordre de paiement via l'API Business |
| `300` | Via le canal USSD |
| `500` | Demande de Paiement marchand sur site |
| `520` | Demande de Paiement e-commerce à la livraison |
| `521` | Demande de Paiement e-commerce immédiat |
| `631` | Demande de paiement provenant d'un particulier |
| `401` | Autres demandes de paiement de facture |

### ExternalOrganisationIdentification1Code

| Code | Nom | Description |
|------|-----|-------------|
| `TXID` | TaxIdentificationNumber | Numéro d'identification fiscale — pour `<OrgId>` (types B et G) |

### ExternalPersonIdentification1Code

| Code | Nom | Description |
|------|-----|-------------|
| `CCPT` | PassportNumber | Numéro attribué par une autorité pour identifier le numéro de passeport d'une personne |
| `NIDN` | NationalIdentityNumber | Numéro attribué par une autorité pour identifier le numéro d'identité national d'une personne |
| `POID` | PersonCommercialIdentification | Identification commerciale de la personne physique commerçante (Numéro de registre de commerce) — **type C uniquement**, ajouté en spec v4.0.0 |
| `TXID` | TaxIdentificationNumber | Numéro d'identification fiscale — ajouté pour type C en spec v4.0.4 |

> **Note sandbox :** Le XSD sandbox BCEAO restreint `<Cd>` à `{CCPT, NIDN}`. POID et TXID ne sont pas encore supportés via le champ `<Cd>` pour `<PrvtId>`. Pour le type C, utiliser `numeroRCCMClientPaye` (sans `<SchmeNm>`) à la place de `identificationFiscaleCommercantPaye`.

### DocumentType6Code

| Code | Nom | Description |
|------|-----|-------------|
| `CINV` | CommercialInvoice | Le document est une facture |
| `CMCN` | CommercialContract | Le document est un contrat entre les parties stipulant les termes et les conditions de la livraison de biens ou de services |
| `DISP` | DispatchAdvice | Le document est un avis d'expédition |
| `PUOR` | PurchaseOrder | Le document est un bon de commande |

### PITypeLigneDetails

| Code | Description |
|------|-------------|
| `PICO` | Retrait en plus d'un achat |
| `PI` | Achat |
| `PICASH` | Retrait |

### PiServiceLevel

| Code | Description |
|------|-------------|
| `PRMG` | Paiement programmé |
| `DISP` | Message de confirmation de disponibilité |

---

## Tableau de cohérences — Canaux × Catégories de service

> Ce tableau définit quelles combinaisons payeur/payé sont autorisées par canal.
> P=Personne physique, B=Entreprise, G=Gouvernement, C=Commerçant
> P2P = payeur P payé P, B2C = payeur B payé C, etc.

| Canal | P2P | P2C | P2B | P2G | B2P | B2C | B2B | B2G | G2P | G2C | G2B | G2G | C2P | C2C | C2B | C2G |
|-------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 731 — QR Code | ✓ | | | | | | | | | | | | ✓ | | | |
| 633 — Adresse / alias / compte | ✓ | | | | | | | | | | | | ✓ | | | |
| 999 — Ordre bancaire | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 000 — QR Code Statique | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ |
| 400 — QR Code Dynamique | | ✓ | ✓ | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ |
| 733 — API Business | | ✓ | | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | |
| 500 — Marchand sur site | | ✓ | ✓ | | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ |
| 521 — E-commerce immédiat | | ✓ | ✓ | | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ |
| 520 — E-commerce livraison | | | ✓ | ✓ | | ✓ | ✓ | ✓ | | | ✓ | ✓ | | | ✓ | ✓ |
| 631 — Particulier | ✓ | | | ✓ | | | | | ✓ | | | | ✓ | | | |
| 401 — Facture | | | ✓ | ✓ | | ✓ | ✓ | | | | ✓ | ✓ | | | ✓ | ✓ |

---

## Règles métier importantes

### Identification des clients

| Type client | XML path | Champ JSON | Scheme `<Cd>` |
|-------------|----------|------------|--------------|
| **P** (personne physique) | `<PrvtId>/<Othr>` | `systemeIdentificationClientPaye + numeroIdentificationClientPaye` | `NIDN` ou `CCPT` |
| **B** (entreprise) | `<OrgId>/<Othr>` | `systemeIdentificationClientPayeur + numeroIdentificationClientPayeur` | `TXID` |
| **G** (gouvernement) | `<OrgId>/<Othr>` | `systemeIdentificationClientPayeur + numeroIdentificationClientPayeur` | `TXID` |
| **C** (commerçant) | `<PrvtId>/<Othr>` | `numeroRCCMClientPaye` (sans `<SchmeNm>`) | aucun |
| **TRAL** | aucun `<Id>` | aucun champ d'identification | — |

> **Règle critique :** Un compte qui n'est pas de type `TRAL` doit obligatoirement avoir une identification. L'absence d'identification retourne l'erreur ADMI.002 "Un compte qui n'est pas de type 'TRAL' doit avoir une identification".

### Identification du compte (`<Id>`)

| TypeCompte | Balise XML | Champ JSON |
|-----------|-----------|------------|
| `CACC`, `SVGS`, `LLSV`, `VACC`, `TAXE` | `<IBAN>` | `ibanClientPaye` |
| `TRAN`, `TRAL` | `<Othr>/<Id>` | `otherClientPaye` |

### `identifiantTransaction` (TxId)

Obligatoire pour les canaux : `400`, `733`, `500`, `521`, `520`, `631`, `401`.

Format auto-généré si non fourni : `TX` + 20 derniers caractères du `endToEndId`.

### `RemittanceInformation` — exclusion mutuelle

- **`Ustrd`** (`motif`) : utilisé seul, texte libre
- **`Strd`** (`typeDocumentReference`, `numeroDocumentReference`, `montantAchat`, `montantRetrait`, `fraisRetrait`, `referenceBulk`) : utilisé seul, données structurées
- Les deux **ne peuvent pas coexister** dans le même message

### Localisation GPS obligatoire

Les canaux suivants exigent `latitudeClientPayeur` + `longitudeClientPayeur` :
`731`, `633`, `000`, `400`, `500`, `521`, `520`, `631`, `401`

### Règles canal/type client

- Les canaux `520`, `521`, `401` **interdisent** les bénéficiaires de type `P` (personne physique) et `C` (commerçant)
- Le canal `731` est réservé aux flux P2P et C2P
- Le canal `631` est réservé aux flux P2P, G2P et C2P

---

## Historique des versions

| Version | Date | Modifications clés |
|---------|------|--------------------|
| **4.0.6** | 2025-08-05 | Remplacement des libellés canaux (QR Code → Envoi, adresse de paiement → etc.). Ajout du mot "Paiement" dans toutes les définitions contenant "Transfert" |
| **4.0.5** | 2025-02-05 | Ajout du type de compte `TAXE` dans `ExternalCashAccountType1Code` |
| **4.0.4** | 2025-04-22 | Ajout `TXID` dans `ExternalPersonIdentification1Code` (valable pour les personnes physiques commerçantes). Règles de gestion du champ `InstructionIdentification`. Règle: le bloc `Unstructured` soit `Structured` peut être renseigné dans `RemittanceInformation` |
| **4.0.3** | 2024-11-13 | Ajout du niveau de service `DISP` (confirmation de disponibilité) |
| **4.0.2** | 2024-08-30 | Nouveau type de compte `VACC` pour les tontines |
| **4.0.1** | 2024-11-07 | Modification: règle de la ville du client (`TwnNm`) pour TRAL et POID |
| **4.0.0** | 2024-10-06 | Ajout `POID` pour les commerçants (type C). `TwnNm` obligatoire pour B, G, C avec POID. `TxId` obligatoire pour canal 400. Canal 733 pour API Business. Balise `Name` dans `CreditorAccount` et `DebtorAccount`. Tableau de cohérences canaux/catégories. Suppression: `CategoryPurpose`, codes de service propriétaires PI, `BranchId` dans `DebtorAgent`/`CreditorAgent` |
| **3.0.0** | 2024-01-03 | Cardinalité `Identification` Creditor/Debtor → `[0..1]`. Cardinalité `Other` de `PrivateIdentification` → `[0..2]` (pour NIDN + POID). Ajout `POID` pour commerçants. Ajout bloc `Structured` dans `RemittanceInformation`. Ajout type `TRAL`. |
| **2.4** | 2023-10-11 | Ajout canal 333 (RTP) et canal 400 (QR Code Dynamique) |
| **2.0** | 2023-05-22 | Données d'identification client (`<Id>`). Géolocalisation du payeur dans `<PstlAdr>` (latitude/longitude). Correction `AcceptanceDateTime`. |

---

## Exemples de messages XML

### Exemple 1 — Paiement standard (P2P, canal 500)

```xml
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>MSNB001...</MsgId>
      <CreDtTm>2024-02-21T15:35:54.362Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf><SttlmMtd>CLRG</SttlmMtd></SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <EndToEndId>ESNB001...</EndToEndId>
        <TxId>1234-456789-456778-1234</TxId>
      </PmtId>
      <PmtTpInf>
        <SvcLvl><Prtry>100000</Prtry></SvcLvl>
        <LclInstrm><Prtry>500</Prtry></LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="XOF">100000</IntrBkSttlmAmt>
      <AccptncDtTm>2024-02-21T15:35:54.1992Z</AccptncDtTm>
      <ChrgBr>SLEV</ChrgBr>
      <!-- Débiteur (Payeur, type P) -->
      <Dbtr>
        <Nm>DIOP</Nm>
        <PstlAdr>
          <AdrLine>48.862725</AdrLine>
          <AdrLine>2.287592</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id><IBAN>SN22SN0343430000003122583876</IBAN></Id>
        <Tp><Cd>CACC</Cd></Tp>
        <Ccy>XOF</Ccy>
        <Nm>P</Nm>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <Othr><Id>SNB001</Id></Othr>
        </FinInstnId>
      </DbtrAgt>
      <!-- Créditeur (Payé) -->
      <CdtrAgt>
        <FinInstnId>
          <Othr><Id>ML C000</Id></Othr>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>Diop Freres</Nm>
        <Id>
          <PrvtId>
            <Othr>
              <Id>0956812345</Id>
              <SchmeNm><Cd>NIDN</Cd></SchmeNm>
            </Othr>
          </PrvtId>
        </Id>
      </Cdtr>
      <CdtrAcct>
        <Id><Othr><Id>123456789</Id></Othr></Id>
        <Tp><Cd>TRAN</Cd></Tp>
        <Ccy>XOF</Ccy>
        <Nm>P</Nm>
        <Prxy><Id>idb4f7c90a-c746-11ed-afa1-0242ac120002</Id></Prxy>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

### Exemple 2 — Paiement P2C avec commerçant (POID + KYC Allégé payeur)

> Utilisation de `TxId` pour référence produit, `RmtInf/Strd` avec numéro de facture.
> Le payeur est un particulier avec KYC Allégé (TRAL — pas d'`<Id>`).
> Le payé est un commerçant individuel (type C) avec NIDN + POID.

```xml
<Dbtr>
  <!-- Payeur type P, TRAL — pas d'<Id> -->
  <Nm>Khady DIOP</Nm>
  <PstlAdr>
    <TwnNm>Dakar</TwnNm>
    <AdrLine>48.862725</AdrLine>
    <AdrLine>2.287592</AdrLine>
  </PstlAdr>
  <CtryOfRes>SN</CtryOfRes>
</Dbtr>
<DbtrAcct>
  <!-- Compte TRAL — <Othr> et pas IBAN -->
  <Id><IBAN>SN22SN034343000000312258387e</IBAN></Id>
  <Tp><Prtry>TRAL</Prtry></Tp>
  ...
</DbtrAcct>

<Cdtr>
  <!-- Payé type C — NIDN + POID (spec v4.0.0+) -->
  <Nm>Khady DIOP</Nm>
  <Id>
    <PrvtId>
      <Othr>
        <Id>0956812345</Id>
        <SchmeNm><Cd>NIDN</Cd></SchmeNm>   <!-- Identité personnelle -->
      </Othr>
      <Othr>
        <Id>ME-NIM-01-2021-B12-B1617</Id>
        <SchmeNm><Cd>POID</Cd></SchmeNm>   <!-- Numéro RCCM — spec v4+ seulement -->
      </Othr>
    </PrvtId>
  </Id>
</Cdtr>
```

> **Note sandbox :** Dans le sandbox BCEAO (XSD pré-v4.0.0), `<Cd>POID</Cd>` est invalide. Utiliser uniquement `<Othr><Id>rccm_value</Id></Othr>` sans `<SchmeNm>` via le champ JSON `numeroRCCMClientPaye`.

### Exemple 3 — Test de disponibilité (DISP, canal 999)

> Envoyé obligatoirement toutes les 5 minutes par le participant au AIP.
> Payeur = Payé (même client). En cas de non-réception, le participant est désactivé.

```xml
<PmtTpInf>
  <SvcLvl><Prtry>DISP</Prtry></SvcLvl>
  <LclInstrm><Prtry>999</Prtry></LclInstrm>
</PmtTpInf>
<IntrBkSttlmAmt Ccy="XOF">1</IntrBkSttlmAmt>
```

> Le client payeur est identique au client payé. `<Cd>NIDN</Cd>` dans `<PrvtId>/<Othr>/<SchmeNm>`.

### Exemple 4 — Paiement de masse de salaires (B2B, canal 999)

> Utilisation de `InstrId` dans les pacs.008 pour garder la référence du groupe.
> Le payeur est une entreprise (type B) avec identification fiscale TXID.

```xml
<Dbtr>
  <Nm>Entreprise AC</Nm>
  <Id>
    <OrgId>
      <Othr>
        <Id>CI40481401025</Id>
        <SchmeNm><Cd>TXID</Cd></SchmeNm>   <!-- Identification fiscale B/G -->
      </Othr>
    </OrgId>
  </Id>
</Dbtr>
```

---

## Mapping JSON ↔ XML — Référence rapide

| Champ JSON PI-SPI | Balise XML ISO 20022 | Notes |
|-------------------|---------------------|-------|
| `msgId` | `<GrpHdr>/<MsgId>` | Format: M+CodeMembre(6)+UNIQUEID(28) |
| `endToEndId` | `<PmtId>/<EndToEndId>` | Format: E+CodeMembre(6)+Date(14)+UNIQUEID(14) |
| `identifiantTransaction` | `<PmtId>/<TxId>` | Obligatoire pour canaux 400, 733, 500, 521, 520, 631, 401 |
| `canalCommunication` | `<PmtTpInf>/<SvcLvl>/<Prtry>` | Code numérique (400, 500, etc.) |
| `montant` | `<IntrBkSttlmAmt Ccy="XOF">` | Toujours XOF, entier |
| `dateHeureAcceptation` | `<AccptncDtTm>` | ISO 8601 |
| `nomClientPayeur` | `<Dbtr>/<Nm>` | Pour B: `denominationSociale` |
| `paysClientPayeur` | `<Dbtr>/<PstlAdr>/<CtryOfRes>` | ISO 3166-2 |
| `villeClientPayeur` | `<Dbtr>/<PstlAdr>/<TwnNm>` | Obligatoire pour B, G, C |
| `adresseClientPayeur` / `latitudeClientPayeur` | `<Dbtr>/<PstlAdr>/<AdrLine>[0]` | Latitude en degrés décimaux |
| `longitudeClientPayeur` | `<Dbtr>/<PstlAdr>/<AdrLine>[1]` | Longitude en degrés décimaux |
| `typeClientPayeur` | Détermine `<OrgId>` vs `<PrvtId>` | B/G→OrgId, P/C→PrvtId |
| `systemeIdentificationClientPayeur` | `<Dbtr>/<Id>/.../<Othr>/<SchmeNm>/<Cd>` | TXID (B/G), NIDN/CCPT (P) |
| `numeroIdentificationClientPayeur` | `<Dbtr>/<Id>/.../<Othr>/<Id>` | Valeur de l'identifiant |
| `ibanClientPayeur` | `<DbtrAcct>/<Id>/<IBAN>` | Pour CACC/SVGS/LLSV/VACC/TAXE |
| `otherClientPayeur` | `<DbtrAcct>/<Id>/<Othr>/<Id>` | Pour TRAN/TRAL |
| `typeCompteClientPayeur` | `<DbtrAcct>/<Tp>/<Cd>` ou `<Prtry>` | TRAL→Prtry, autres→Cd |
| `aliasClientPayeur` | `<DbtrAcct>/<Prxy>/<Id>` | UUID ou numéro |
| `codeMembreParticipantPayeur` | `<DbtrAgt>/<FinInstnId>/<Othr>/<Id>` | Code 6 positions |
| `codeMembreParticipantPaye` | `<CdtrAgt>/<FinInstnId>/<Othr>/<Id>` | Code 6 positions |
| `nomClientPaye` | `<Cdtr>/<Nm>` | — |
| `numeroRCCMClientPaye` | `<Cdtr>/<Id>/<PrvtId>/<Othr>/<Id>` | Type C, sans `<SchmeNm>` |
| `ibanClientPaye` | `<CdtrAcct>/<Id>/<IBAN>` | Pour CACC/SVGS/LLSV/VACC/TAXE |
| `otherClientPaye` | `<CdtrAcct>/<Id>/<Othr>/<Id>` | Pour TRAN/TRAL |
| `aliasClientPaye` | `<CdtrAcct>/<Prxy>/<Id>` | UUID ou numéro |
| `motif` | `<RmtInf>/<Ustrd>` | Exclusif avec Strd |
| `typeDocumentReference` | `<RmtInf>/<Strd>/<RfrdDocInf>/<Tp>/<CdOrPrtry>/<Cd>` | CINV, CMCN, DISP, PUOR |
| `numeroDocumentReference` | `<RmtInf>/<Strd>/<RfrdDocInf>/<Nb>` | — |
| `montantAchat` | `<RmtInf>/<Strd>/<RfrdDocAmt>/<RmtdAmt>` | — |
| `montantRetrait` | `<RmtInf>/<Strd>/<AddtlRmtInf>` (ligne PICASH) | — |
| `fraisRetrait` | `<RmtInf>/<Strd>/<AddtlRmtInf>` (ligne PICO) | — |
| `referenceBulk` | `<RmtInf>/<Strd>` | Dans le bloc Strd, exclusif avec Ustrd |
