# Flux d'annulation — CAMT.056 / CAMT.029 / PACS.004

Documentation de référence pour le flux BCEAO §4.8 « Annulation d'un transfert ou d'un paiement ». Couvre les 3 messages ISO 20022, les 3 entités locales (`PiTransfer`, `PiReturnRequest`, `PiReturnExecution`), et les 3 scénarios BCEAO de traitement à la réception d'un camt.056.

---

## 1. Vue d'ensemble — entités vs messages

```mermaid
flowchart LR
    subgraph ISO[Messages ISO 20022]
      direction TB
      M008[PACS.008<br/>virement initial]
      M056[CAMT.056<br/>demande d'annulation]
      M029[CAMT.029<br/>rejet de la demande]
      M004[PACS.004<br/>retour de fonds effectif]
    end

    subgraph DB[Entités locales]
      direction TB
      T[PiTransfer<br/>statut: PEND→ACCC→RTND]
      R[PiReturnRequest<br/>statut: PENDING→ACCEPTED|RJCR]
      E[PiReturnExecution<br/>montantRetourne, raisonRetour]
    end

    M008 -.persist.-> T
    M056 -.persist.-> R
    M029 -.update statut.-> R
    M004 -.persist.-> E
    M004 -.update statut.-> T
    R -.returnRequestId.-> E
```

**Distinction clé** :
- `PiReturnRequest` = « quelqu'un *demande* l'annulation » — peut rester PENDING ou finir RJCR sans mouvement de fonds.
- `PiReturnExecution` = « les fonds *ont été* retournés » — preuve matérielle du mouvement, peuple les rapports de réconciliation.

---

## 2. Flux nominal — annulation acceptée par le client payé

Cas le plus courant : payeur demande l'annulation, payé accepte, fonds retournés.

```mermaid
sequenceDiagram
    participant ClPyr as Client payeur
    participant Pyr as PI-SPI Payeur
    participant AIP
    participant Pye as PI-SPI Payé
    participant ClPye as Client payé

    Note over Pyr,Pye: Le PACS.008 initial est déjà passé,<br/>PiTransfer côté payeur=ACCC, côté payé=ACCC

    ClPyr->>Pyr: PUT /transferts/{id}/annulations<br/>raison=AM09
    Pyr->>Pyr: garde irrévocable + dédup PENDING
    Pyr->>Pyr: PiReturnRequest OUTBOUND PENDING
    Pyr->>AIP: POST /retour-fonds/demande<br/>(camt.056)

    AIP->>Pye: POST /retour-fonds/demande<br/>(camt.056 INBOUND)
    Pye->>Pye: idempotence + B1/B2 checks
    Pye->>Pye: PiReturnRequest INBOUND PENDING
    Pye->>ClPye: webhook RETURN_REQUEST_RECEIVED

    ClPye-->>Pye: décision = ACCEPT
    Pye->>Pye: acceptReturn — garde idempotence
    Pye->>Pye: PiReturnExecution OUTBOUND<br/>(returnRequestId lié)
    Pye->>Pye: PiReturnRequest INBOUND → ACCEPTED
    Pye->>Pye: PiTransfer INBOUND → RTND<br/>(codeRaison=CUST)
    Pye->>AIP: POST /retour-fonds<br/>(pacs.004 raisonRetour=CUST)

    AIP->>Pyr: POST /retour-fonds<br/>(pacs.004 INBOUND)
    Pyr->>Pyr: PiReturnExecution INBOUND<br/>(returnRequestId lié)
    Pyr->>Pyr: PiReturnRequest OUTBOUND → ACCEPTED
    Pyr->>Pyr: PiTransfer OUTBOUND → RTND<br/>(codeRaison=CUST)
    Pyr->>ClPyr: webhook RETURN_EXECUTED
```

**Symétrie post-traitement** :

| Côté | PiTransfer | PiReturnRequest | PiReturnExecution |
|---|---|---|---|
| Payeur | OUTBOUND `RTND` | OUTBOUND `ACCEPTED` | INBOUND |
| Payé | INBOUND `RTND` | INBOUND `ACCEPTED` | OUTBOUND |

---

## 3. Flux rejet — le client payé refuse l'annulation

```mermaid
sequenceDiagram
    participant Pyr as PI-SPI Payeur
    participant AIP
    participant Pye as PI-SPI Payé
    participant ClPye as Client payé

    Pyr->>AIP: camt.056 (raison=AM09)
    AIP->>Pye: camt.056 INBOUND
    Pye->>Pye: PiReturnRequest INBOUND PENDING
    Pye->>ClPye: webhook RETURN_REQUEST_RECEIVED

    ClPye-->>Pye: décision = REJECT
    Pye->>Pye: rejectReturn(raison=CUST)
    Pye->>Pye: PiReturnRequest INBOUND → RJCR<br/>(raisonRejet=CUST)
    Note over Pye: PiTransfer INBOUND<br/>reste à ACCC (intact)
    Pye->>AIP: camt.029 (statut=RJCR, raison=CUST)

    AIP->>Pyr: camt.029 INBOUND
    Pyr->>Pyr: PiReturnRequest OUTBOUND → RJCR<br/>(raisonRejet=CUST)
    Note over Pyr: PiTransfer OUTBOUND<br/>reste à ACCC (intact)
```

**Aucun `PiReturnExecution` créé** — pas de mouvement de fonds. Les `PiTransfer` restent à `ACCC` des deux côtés.

---

## 4. Auto-rejet B1 — transfert déjà retourné (ARDT)

BCEAO §4.8 : « Si le transfert de fonds a déjà été retournée, PI rejette la demande en envoyant un camt.029 avec le code `ARDT`. »

```mermaid
sequenceDiagram
    participant Pyr as PI-SPI Payeur
    participant AIP
    participant Pye as PI-SPI Payé

    Note over Pye: État local : PiTransfer INBOUND=RTND<br/>OU PiReturnExecution OUTBOUND existe<br/>(annulation antérieure acceptée)

    Pyr->>AIP: camt.056 (retry ou nouveau client)
    AIP->>Pye: camt.056 INBOUND

    Pye->>Pye: dédup msgId : OK
    Pye->>Pye: idempotence PENDING : aucun doublon
    Pye->>Pye: check B1 — alreadyReturned ?<br/>PiTransfer.statut=RTND ✓<br/>OU PiReturnExecution.findByE2e ✓

    Note over Pye: Auto-rejet sans notifier le client
    Pye->>Pye: PiReturnRequest INBOUND<br/>statut=RJCR direct, raisonRejet=ARDT
    Pye->>AIP: camt.029 (statut=RJCR, raison=ARDT)

    AIP->>Pyr: camt.029 INBOUND<br/>(la demande retry est rejetée)
```

**Sans webhook** côté payé — BCEAO l'impose explicitement (« sans notifier le client »).

---

## 5. Auto-rejet B2 — compte client clôturé (AC04)

BCEAO §4.8 : « Si le client a clôturé son compte dans vos livres, vous devez rejeter directement en utilisant le code `AC04`. »

```mermaid
sequenceDiagram
    participant Pyr as PI-SPI Payeur
    participant AIP
    participant Pye as PI-SPI Payé
    participant Bk as Backend Payé

    Pyr->>AIP: camt.056
    AIP->>Pye: camt.056 INBOUND
    Pye->>Pye: dédup + B1 check : OK<br/>(transfert pas encore retourné)
    Pye->>Pye: PiReturnRequest INBOUND PENDING
    Pye->>Bk: webhook RETURN_REQUEST_RECEIVED

    Note over Bk: Backend constate : compte payé clôturé<br/>(donnée hors PI-SPI)
    Bk->>Pye: PUT /retour-fonds/{id}/rejets<br/>raison=AC04
    Pye->>Pye: rejectReturn(raison=AC04)
    Pye->>Pye: PiReturnRequest INBOUND → RJCR<br/>(raisonRejet=AC04)
    Pye->>AIP: camt.029 (statut=RJCR, raison=AC04)
    AIP->>Pyr: camt.029 INBOUND
```

**B2 délégué au backend** — PI-SPI ne tracke pas le statut compte (responsabilité du back-office métier). Le hook `RETURN_REQUEST_RECEIVED` permet au backend d'auto-rejeter sans intervention humaine.

---

## 6. State machine — `PiTransfer.statut`

```mermaid
stateDiagram-v2
    [*] --> INITIE: POST /transferts<br/>(snapshot, rien émis)
    INITIE --> PEND: PUT /transferts/{id}<br/>(PACS.008 émis)
    INITIE --> [*]: DELETE (annule local)

    PEND --> ACCC: PACS.002 INBOUND<br/>ACCC|ACSC|ACSP*
    PEND --> RJCT: PACS.002 INBOUND<br/>RJCT
    PEND --> TMOT: timeout AIP
    PEND --> ECHEC: ADMI.002 (rejet structurel)

    ACCC --> RTND: PACS.004 reçu (côté payeur)<br/>OU acceptReturn (côté payé)

    RJCT --> [*]
    TMOT --> [*]
    ECHEC --> [*]
    RTND --> [*]

    note right of ACCC
        Statut uniformisé pour tous
        les transferts réussis (cf.
        TransferStatus.normalizeSuccess).
        ACSP est mappé en ACCC localement.
    end note

    note right of RTND
        Terminal — distinct de RJCT.
        codeRaison stocke la raisonRetour
        (CUST, FR01, AC06, etc.)
    end note
```

`*` ACSP est mappé en ACCC localement par `TransferStatus.normalizeSuccess`. Le payload OUTBOUND vers l'AIP continue à porter ACSP comme l'impose BCEAO.

---

## 7. State machine — `PiReturnRequest.statut`

```mermaid
stateDiagram-v2
    [*] --> PENDING: camt.056 émis ou reçu
    PENDING --> ACCEPTED: pacs.004<br/>(émis ou reçu)
    PENDING --> RJCR: camt.029<br/>(émis ou reçu, ou auto-rejet B1/B2)
    PENDING --> TIMEOUT: aucune réponse AIP

    ACCEPTED --> [*]
    RJCR --> [*]
    TIMEOUT --> [*]

    note right of ACCEPTED
        Lié à un PiReturnExecution
        via returnRequestId.
        PiTransfer associé → RTND.
    end note

    note right of RJCR
        raisonRejet renseigné :
        CUST | ARDT | AC04 | …
        PiTransfer associé reste intact.
    end note
```

---

## 8. Relations entre entités

```mermaid
erDiagram
    PiTransfer ||--o{ PiReturnRequest : "endToEndId"
    PiReturnRequest ||--o| PiReturnExecution : "returnRequestId"

    PiTransfer {
      string endToEndId PK
      enum direction
      enum statut "PEND ACCC RTND ..."
      string codeRaison "FR01 / CUST si RTND"
    }

    PiReturnRequest {
      Long id PK
      string identifiantDemande
      string endToEndId
      enum direction
      enum statut "PENDING ACCEPTED RJCR"
      enum raison "AC03 FRAD DUPL AM09 SVNR"
      enum raisonRejet "CUST ARDT AC04 ..."
    }

    PiReturnExecution {
      Long id PK
      string msgId UK
      string endToEndId
      enum direction
      decimal montantRetourne
      enum raisonRetour
      Long returnRequestId FK
    }
```

**Cardinalité** :
- 1 transfer ↔ N requests (BCEAO autorise les retries de camt.056 tant que pas accepté).
- 1 request ↔ 0..1 execution (une exécution n'apparaît qu'à l'acceptation finale).

---

## 9. Codes raison — par message

| Message | Champ | Codes valides BCEAO |
|---|---|---|
| **CAMT.056** (demande d'annulation) | `raison` | `DUPL` `AC03` `AM09` `SVNR` `FRAD` |
| **CAMT.029** (rejet de la demande) | `raison` | `CUST` (décision client) `ARDT` (déjà retourné) `AC04` (compte clôturé) + autres §5.6 |
| **PACS.004** (retour de fonds) | `raisonRetour` | `CUST` (décision client) `FR01` (fraude) `AC06` `AC07` `MD06` `BE01` `RR04` |
| **ADMI.002** (rejet PI du camt.056) | `codeRaison` | `TransactionNotFound` `AG01` `AG08` `CH17` `AG10` `AG11` |

Les enums Java (`TransactionCancelReason`, `CodeRaisonDemandeRetourFonds`, `CodeRaisonRetourFonds`) sont alignés sur ces patterns.

---

## 10. Points d'attention BCEAO §4.8

- **Pas de garantie de réponse** après l'envoi d'un camt.056 — le payé peut ignorer la demande indéfiniment. Côté local, la `PiReturnRequest` reste PENDING ; un timeout applicatif (à câbler) doit la passer en `TIMEOUT` après N jours.
- **Retries autorisés** côté payeur tant que pas acceptée. Notre garde dédup PENDING bloque les retries simultanés mais autorise un nouveau camt.056 après un RJCR.
- **Le code `FRAD`** est typiquement émis par le participant payé à l'initiative de sa supervision interne — pas seulement à la demande client (cas légèrement à part dans la liste).
- **Idempotence** stricte sur `msgId` (dédup AIP) + idempotence métier sur `(endToEndId, direction, statut=PENDING)` (dédup retries client).
