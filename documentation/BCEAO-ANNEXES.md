# BCEAO PI-AIP — Annexes: Référentiel des codes

> Source: BCEAO-PI-AIP-MODELE-DONNEES — Section 5. Annexes (pp. 203–217)
> Schéma des données échangées entre le participant et l'application d'interfaçage (AIP)

---

## 5.1. CanalCommunication

Canaux utilisés dans les **transferts PACS.008**.

| Code | Canal |
|------|-------|
| `731` | Transfert par QR Code |
| `633` | Transfert par adresse de paiement |
| `633` | Transfert par alias numéro de téléphone |
| `633` | Transfert par compte |
| `999` | Ordre de transfert bancaire |
| `000` | Paiement par QR Code Statique |
| `400` | Paiement par QR Code Dynamique |
| `733` | Ordre de paiement via l'API Business |
| `401` | Autres demandes de paiement de facture |
| `500` | Demande de Paiement marchand sur site |
| `520` | Demande de Paiement e-commerce à la livraison |
| `521` | Demande de Paiement e-commerce immédiat |
| `631` | Demande de paiement provenant d'un particulier |
| `300` | Via le canal USSD |

> **Note d'implémentation :** le code `633` couvre trois usages distincts (adresse, alias téléphone, compte). Le canal détermine aussi les règles de localisation GPS obligatoires et la cohérence des types de clients autorisés (voir tableau de cohérences §4 du modèle de données).

---

## 5.2. TypeCompte

Types de comptes pouvant être associés à un alias ou utilisés dans une transaction.

| Code | Nom | Description |
|------|-----|-------------|
| `CACC` | Compte courant | Compte utilisé pour enregistrer des débits et des crédits lorsqu'aucun compte spécifique n'a été désigné |
| `SVGS` | Compte épargne | Compte utilisé pour l'épargne |
| `TRAN` | Compte de transaction | Un compte de paiement, ou un compte de monnaie électronique ou un compte de SFD |
| `LLSV` | Tirelire | Compte utilisé pour le tirelire |
| `TRAL` | Compte de transaction sans KYC | Compte de transaction allégé (sans KYC) — plafond 200 000 XOF |
| `VACC` | Compte de tontine | Compte virtuel utilisé pour les tontines |
| `TAXE` | Taxe | Compte ouvert dans les livres de la BCEAO pour le trésor public |

> **Note d'implémentation :** `TRAL` est le seul type exempt de l'obligation d'identification dans le PACS.008 (pas de `<Id>` requis). Les comptes `CACC`, `SVGS`, `LLSV`, `VACC`, `TAXE` exigent un IBAN ; `TRAN` utilise le champ `other` (numéro de téléphone) ; `TRAL` utilise `other`.

---

## 5.3. TypeAlias

Types d'alias enregistrés dans le registre PI-RAC.

| Code | Nom | Description |
|------|-----|-------------|
| `SHID` | SchemeIdentificationNumber | Alias de compte émis par un système de paiement |
| `MBNO` | MobilePhoneNumber | Numéro de téléphone mobile avec l'indicatif du pays |
| `MCOD` | MerchantCode | Identifiant de code marchand |

---

## 5.4. Pays

Codes ISO 3166-1 alpha-2 des pays membres de l'UEMOA acceptés par le système PI.

| Code | Pays |
|------|------|
| `BJ` | Bénin |
| `BF` | Burkina Faso |
| `CI` | Côte d'Ivoire |
| `GW` | Guinée-Bissau |
| `ML` | Mali |
| `NE` | Niger |
| `SN` | Sénégal |
| `TG` | Togo |

---

## 5.5. Statut (PACS.002)

Statuts de règlement retournés par l'AIP dans les messages PACS.002.

| Code | Nom | Description |
|------|-----|-------------|
| `ACCC` | AcceptedSettlementCompletedCreditorAccount | Règlement effectué sur le compte du participant payé |
| `ACSC` | AcceptedSettlementCompletedDebitorAccount | Règlement effectué sur le compte du participant payeur |
| `ACSP` | AcceptedSettlementInProcess | Règlement en cours d'exécution suite à la validation des contrôles de format et fonctionnel |
| `RJCT` | Rejected | Instruction de paiement rejetée |

---

## 5.6. CodeRaison

Codes de raison de rejet utilisés dans les PACS.002 / PAIN.014 / ADMI.002.

### Codes techniques / système

| Code | Nom | Description |
|------|-----|-------------|
| `AB03` | AbortedSettlementTimeout | Message en Timeout |
| `AB04` | AbortedSettlementFatalError | Transaction rejetée à cause d'une erreur fatale |
| `AB08` | OfflineCreditorAgent | Le système du participant payé n'est pas accessible |
| `AB09` | ErrorCreditorAgent | Transaction rejetée à cause d'une erreur chez le participant payé |
| `DT02` | InvalidCreationDate | La date de création du message est invalide |
| `DU04` | DuplicateEndToEndID | Le endToEndId est dupliqué — doit être unique sur l'ensemble des transactions |
| `NARR` | Narrative | La raison de l'erreur est détaillée dans le champ NARR |

### Codes compte

| Code | Nom | Description |
|------|-----|-------------|
| `AC03` | InvalidCreditorAccountNumber | Le numéro de compte du payé est invalide |
| `AC04` | ClosedAccountNumber | Numéro de compte payeur clôturé |
| `AC06` | BlockedAccount | Le compte spécifié est bloqué |
| `AC07` | ClosedCreditorAccountNumber | Numéro de compte payé clôturé |
| `RC04` | InvalidCreditorBankIdentifier | L'identifiant bancaire du créancier est invalide ou manquant |

### Codes accès / autorisation

| Code | Nom | Description |
|------|-----|-------------|
| `AG01` | TransactionForbidden | Transaction interdite sur ce type de compte |
| `AG03` | TransactionNotSupported | Le client n'est pas autorisé à payer en mode debit différé |
| `AG08` | InvalidAccessRights | La transaction a échoué en raison de droits d'accès manquants |
| `AG10` | AgentSuspended | Le participant payeur est suspendu |
| `AG11` | CreditorAgentSuspended | L'agent créancier du message est suspendu du système de paiement en temps réel |

### Codes montant

| Code | Nom | Description |
|------|-----|-------------|
| `AM02` | NotAllowedAmount | Le montant de la transaction/message spécifique est supérieur au maximum autorisé |
| `AM04` | InsufficientFunds | Le solde de garantie du participant est insuffisant |
| `AM09` | WrongAmount | Le montant reçu ne correspond pas au montant convenu ou attendu |
| `AM14` | AmountExceedsAgreedLimit | Le montant de la transaction fait dépasser le plafond de débit différé du client |
| `AM21` | LimitExceeded | Le montant de la transaction dépasse les limites convenues entre la banque et le client |

### Codes identification client

| Code | Nom | Description |
|------|-----|-------------|
| `BE01` | InconsistentWithEndCustomer | L'identification du client final n'est pas liée au numéro de compte associé |
| `BE05` | UnrecognisedInitiatingParty | La partie qui a initié le message n'est pas reconnue par le client final |
| `BE17` | InvalidCreditorIdentificationCode | Code d'identification du payé ou du créditeur final manquant ou invalide |
| `CH17` | ElementNotAdmitted | La transaction ne peut être un PP2P alors que le payeur et le payé ne sont pas des personnes physiques |

### Codes signature / certificat

| Code | Nom | Description |
|------|-----|-------------|
| `DS0A` | DataSignRequested | La balise signature est inexistante |
| `DS0B` | UnknownDataSignFormat | La signature des données pour le format n'est pas disponible ou n'est pas valide |
| `DS0C` | SignerCertificateRevoked | Le certificat est révoqué |
| `DS0D` | SignerCertificateNotValid | Le certificat a expiré |
| `DS0E` | IncorrectSignerCertificate | Le certificat n'est pas contenu dans la balise signature |
| `DS0F` | SignerCertificationAuthoritySignerNotValid | Le certificat n'est pas délivré par l'autorité de certification |
| `DS0H` | NotAllowedAccount | Le certificat ne vous appartient pas |
| `DS04` | OrderRejected | Ordre rejeté par le participant |

### Codes RTP (PAIN.013 / PAIN.014)

| Code | Nom | Description |
|------|-----|-------------|
| `AEXR` | AlreadyExpiredRTP | La demande de paiement a déjà expirée |
| `ALAC` | AlreadyAcceptedRTP | La demande de paiement a déjà été acceptée |
| `APAR` | AlreadyPaidRTP | Le paiement demandé a déjà été effectué par le payeur |
| `ARFR` | AlreadyRefusedRTP | La demande de paiement a déjà été refusée |
| `ARJR` | AlreadyRejectedRTP | La demande de paiement a déjà été rejetée |
| `IRNR` | InitialRTPNeverReceived | La demande de paiement n'a jamais été reçue |
| `RR07` | RemittanceInformationInvalid | Le justificatif de la demande de paiement est invalide (numéro de facture invalide) |

### Codes réglementaires / fraude

| Code | Nom | Description |
|------|-----|-------------|
| `FR01` | Fraud | Retourné à la suite d'une fraude |
| `RR04` | RegulatoryReason | Raison règlementaire — bénéficiaire sur une liste d'interdiction des Nations Unies |

---

## 5.7. CodeRaisonVerification (ACMT.023)

| Code | Nom | Description |
|------|-----|-------------|
| `AC01` | IncorrectAccountNumber | Le numéro de compte est invalide ou manquant |

---

## 5.8. EtatParticipant

États possibles d'un participant dans le système PI.

| Code | Nom | Description |
|------|-----|-------------|
| `JOIN` | Joining | Le participant est en train de s'enrôler dans le système |
| `ENBL` | Enabled | Le participant est actif |
| `DSBL` | Disabled | Le participant est inactif |
| `DLTD` | Deleted | Le participant est supprimé du système PI |

---

## 5.9. CodeRaisonRejet (ADMI.002)

Codes de rejet retournés par l'AIP dans les messages d'erreur système (ADMI.002).

| Code | Description |
|------|-------------|
| `TransactionNotFound` | Référence de la transaction non connue dans le système PI |
| `ReferenceNotFound` | Référence du message non connue dans le système PI |
| `CodeNotFound` | Le code n'est pas connu |
| `DS0A` | La balise signature est inexistante |
| `DS0B` | La signature est invalide |
| `DS0E` | Le certificat n'est pas contenu dans la balise signature |
| `DS0F` | Le certificat n'est pas délivré par l'autorité de certification |
| `DS0D` | Le certificat a expiré |
| `DS0C` | Le certificat est révoqué |
| `DS0H` | Le certificat ne vous appartient pas |
| `AG08` | Les règles ne vous permettent pas d'envoyer une réponse à une demande dont vous n'êtes pas le destinataire |
| `DU04` | Le endToEndId est dupliqué — doit être unique sur l'ensemble des transactions |
| `RC03` | L'identifiant bancaire du débiteur n'est pas valide ou est manquant |
| `AG10` | Le participant payeur est suspendu du système de paiement en temps réel |
| `AG11` | Le participant payé est suspendu du système de paiement en temps réel |
| `RC04` | La valeur de l'élément msgid ne correspond pas |
| `AC01` | Le numéro de compte est incorrect |
| `AGNT` | Le destinataire de la demande de vérification est incorrect |

---

## 5.10. TypeClient

Catégories de clients dans le système PI.

| Code | Nom |
|------|-----|
| `P` | Personne physique |
| `B` | Personne morale (entreprise) |
| `G` | Gouvernement |
| `C` | Commerçant (personne physique commerçante) |

> **Note d'implémentation :** Le type `C` est une personne physique exerçant une activité commerciale. Son identification dans le PACS.008 utilise le numéro RCCM (`numeroRCCMClientPaye`). Les types `B` et `G` utilisent l'identification fiscale TXID via `<OrgId>`. Les canaux 520, 521 et 401 interdisent les bénéficiaires de type `P` et `C`.

---

## 5.11. TypeTransaction

| Code | Description |
|------|-------------|
| `PRMG` | Paiement programmé |
| `DISP` | Message de confirmation de disponibilité |

---

## 5.12. Format MessageIdentification (MsgId)

Le format du `msgId` est :

```
M (1) + CodeMembre (6) + UNIQUEID (28)
```

**Exemple :** `MCIE002SHEFG2CX363XOYMFR9VB2CBZ8GHK`

- `M` — préfixe fixe (Message)
- `CIE002` — code membre à 6 caractères
- `SHEFG2CX363XOYMFR9VB2CBZ8GHK` — identifiant unique (28 caractères)

---

## 5.13. Identification des participants

Chaque participant est identifié par un **code membre sur 6 positions** sous le format :

```
Code Pays (2) + Code type de participant (1) + Numéro (3)
```

**Exemple :** `CIE002` = Côte d'Ivoire (`CI`) + Établissement (`E`) + `002`

---

## 5.14. Format du EndToEndId

Le format du `endToEndId` est :

```
E (1) + CodeMembre (6) + Date YYYYMMDDHHMMSS (14) + UNIQUEID (14)
```

**Exemple :** `ECIE00220221121161834UNIQUEIDUNIQUE14`

- `E` — préfixe fixe (EndToEnd)
- `CIE002` — code membre
- `20221121161834` — date/heure au format YYYYMMDDHHMMSS
- 14 caractères d'identifiant unique

---

## 5.15. CodeSystemeIdentification

Schèmes d'identification client utilisés dans les champs `systemeIdentificationClient*`.

| Code | Description |
|------|-------------|
| `TXID` | Numéro d'identification fiscale — utilisé pour les types `B` et `G` dans `<OrgId>` |
| `CCPT` | Numéro attribué par une autorité pour identifier le numéro de passeport d'une personne |
| `NIDN` | Numéro attribué par une autorité pour identifier le numéro d'identité national d'une personne |

> **Note d'implémentation :** Le sandbox BCEAO restreint `<PrvtId>/<Othr>/<Cd>` aux valeurs `{CCPT, NIDN}`. Le code `POID` (PersonCommercialIdentification, ajouté en spec v4.0.0 pour le type `C`) n'est pas encore supporté par le XSD sandbox. Pour le type `C`, utiliser `numeroRCCMClientPaye` (sans `<Cd>`) plutôt que `systemeIdentification=POID`.

---

## 5.16. StatutOperationAlias

Statuts de retour des opérations sur alias (création, modification, suppression).

| Code | Description |
|------|-------------|
| `SUCCES` | Instruction complète |
| `ECHEC` | Instruction rejetée |

---

## 5.17. CodeTypeDocument

Types de documents de référence utilisés dans le bloc `RmtInf/Strd` du PACS.008 / PAIN.013.

| Code | Nom | Description |
|------|-----|-------------|
| `CINV` | CommercialInvoice | Le document est une facture |
| `CMCN` | CommercialContract | Le document est un contrat entre les parties stipulant les termes et les conditions de la livraison de biens ou de services |
| `DISP` | DispatchAdvice | Le document est un avis d'expédition |
| `PUOR` | PurchaseOrder | Le document est un bon de commande |

---

## 5.18. CanalCommunicationRTP

Canaux utilisés spécifiquement pour les **demandes de paiement PAIN.013 (Request-to-Pay)**.

| Code | Canal |
|------|-------|
| `401` | Autres demandes de paiement de facture |
| `500` | Demande de Paiement marchand sur site |
| `520` | Demande de Paiement e-commerce à la livraison |
| `521` | Demande de Paiement e-commerce immédiat |
| `631` | Demande de paiement provenant d'un particulier |

> **Note :** Les canaux RTP sont un sous-ensemble des canaux de transfert. Le canal `631` (particulier) doit être remappé en `500` si le payé est de type `B`, `G` ou `C`.

---

## 5.19. Format identifiantDemandeRetourFonds

Le format du champ `identifiantDemandeRetourFonds` (CAMT.056) est :

```
C (1) + CodeMembre (6) + UNIQUEID (28)
```

**Exemple :** `CCIE002UNIQUEIDUNIQUEIDUNIQUEID28`

---

## 5.20. CodeRaisonDemandeRetourFonds (CAMT.056)

Raisons pour initier une demande de retour de fonds.

| Code | Nom | Description |
|------|-----|-------------|
| `DUPL` | DuplicatePayment | Déjà payé |
| `AC03` | InvalidCreditorAccountNumber | Erreur sur le destinataire |
| `AM09` | WrongAmount | Erreur sur le montant |
| `FRAD` | FraudulentOrigin | Annulation demandée à la suite d'une transaction dont l'origine est frauduleuse |
| `SVNR` | ServiceNotRendered | Le paiement est annulé car le produit n'a pas été livré ou le service n'a pas été rendu |

---

## 5.21. CodeStatutDemandeRetourFonds (CAMT.029)

Statut d'une demande de retour de fonds.

| Code | Nom | Description |
|------|-----|-------------|
| `RJCR` | RejectedCancellationRequest | Utilisé lorsqu'une demande d'annulation a été rejetée |

---

## 5.22. CodeRaisonRejetDemandeRetourFonds (CAMT.029)

Raisons de rejet d'une demande de retour de fonds.

| Code | Nom | Description |
|------|-----|-------------|
| `CUST` | CustomerDecision | Lorsque la demande d'annulation (CAMT.056) est rejetée par le client payé |
| `AC04` | ClosedAccountNumber | Le numéro de compte spécifié a été clôturé dans les livres du participant payé |
| `ARDT` | AlreadyReturned | La transaction a déjà été annulée |

---

## 5.23. CodeRaisonRetourFonds (PACS.004)

Raisons d'un retour de fonds effectif.

| Code | Nom | Description |
|------|-----|-------------|
| `AC06` | BlockedAccount | Le compte spécifié est bloqué |
| `AC07` | ClosedCreditorAccountNumber | Numéro de compte du créditeur clôturé |
| `FR01` | Fraud | Retourné à la suite d'une fraude |
| `MD06` | RefundRequestByEndCustomer | Retour de fonds demandé par le client final (bénéficiaire) |
| `BE01` | InconsistentWithEndCustomer | L'identification du client final ne correspond pas au numéro de compte associé, à l'identifiant de l'organisation ou à l'identifiant privé |
| `RR04` | RegulatoryReason | Raison règlementaire — bénéficiaire sur une liste d'interdiction |
| `CUST` | RequestedByCustomer | Retour de fonds demandé par le client payeur |

---

## 5.24. CodeEvenementNotifParticipant

Codes d'événements pour les notifications envoyées aux participants.

| Code | Description |
|------|-------------|
| `PING` | Test de connectivité |
| `MAIN` | Maintenance |

---

## 5.25. CodeEvenementAccusePI

Codes d'événements pour les accusés de réception PI.

| Code | Description |
|------|-------------|
| `PING` | Réponse Test connectivité |

---

## 5.26. CodeEvenementNotifPI

Codes des notifications envoyées par le système PI aux participants.

| Code | Description |
|------|-------------|
| `INFO` | Informations sur le système PI |
| `WARN` | Avertissement envoyé au participant |

---

## 5.27. TypeRapportCompFactTrans

Types de rapports de compensation et de facturation.

| Code | Description |
|------|-------------|
| `COMP` | Solde de compensation |
| `FACT` | Facture sur une période mensuelle |
| `TRANS` | Liste des transactions sur une période de compensation |

---

## 5.28. TypeOperationGarantie

Types d'opérations sur les garanties.

| Code | Description |
|------|-------------|
| `DBIT` | Augmentation de la garantie |
| `CRDT` | Diminution de la garantie |

---

## 5.29. IndicateurSolde

Sens du solde dans les rapports.

| Code | Description |
|------|-------------|
| `DBIT` | Le solde est débiteur |
| `CRDT` | Le solde est créditeur |

---

## 5.30. TypeBalanceCompense

| Code | Description |
|------|-------------|
| `CLBD` | Balance du compte à la fin de la période de déclaration convenue au préalable. Somme de toutes les écritures enregistrées sur le compte pendant la période de déclaration |

---

## 5.31. StatutDemandePaiement (PAIN.014)

Statut de la réponse à une demande de paiement.

| Code | Nom | Description |
|------|-----|-------------|
| `RJCT` | Rejected | Instruction de paiement rejetée |

---

## 5.32. TypeCompteRTPClientPayeur

Types de comptes autorisés pour le **payeur** dans une demande de paiement (PAIN.013).

| Code | Nom | Description |
|------|-----|-------------|
| `CACC` | Compte courant | Aucun compte spécifique désigné |
| `SVGS` | Compte épargne | Compte utilisé pour l'épargne |
| `TRAN` | Compte de transaction | Monnaie électronique ou compte SFD |
| `LLSV` | Tirelire | Compte utilisé pour le tirelire |
| `TRAL` | Compte de transaction sans KYC | Compte allégé (sans KYC) |
| `VACC` | Compte de tontine | Compte virtuel pour les tontines |
| `TAXE` | Taxe | Compte BCEAO pour le trésor public |

---

## 5.33. TypeCompteRTPClientPaye

Types de comptes autorisés pour le **payé** dans une demande de paiement (PAIN.013).

> **Note :** Le type `TRAL` est **interdit** pour le payé dans un RTP.

| Code | Nom | Description |
|------|-----|-------------|
| `CACC` | Compte courant | Aucun compte spécifique désigné |
| `SVGS` | Compte épargne | Compte utilisé pour l'épargne |
| `TRAN` | Compte de transaction | Monnaie électronique ou compte SFD |
| `LLSV` | Tirelire | Compte utilisé pour le tirelire |
| `VACC` | Compte de tontine | Compte virtuel pour les tontines |
| `TAXE` | Taxe | Compte BCEAO pour le trésor public |
