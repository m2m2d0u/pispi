package ci.sycapay.pispi.entity;

import ci.sycapay.pispi.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pi_alias")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PiAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "end_to_end_id", nullable = false, length = 35)
    private String endToEndId;

    @Column(name = "alias_value", length = 50)  // nullable for SHID - PI-RAC generates the value
    private String aliasValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_alias", nullable = false, length = 10)
    private TypeAlias typeAlias;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client", length = 5)
    private TypeClient typeClient;

    @Column(name = "nom", length = 140)
    private String nom;

    @Column(name = "prenom", length = 140)
    private String prenom;

    @Column(name = "lieuNaissance", length = 140)
    private String lieuNaissance;

    @Column(name = "raison_sociale", length = 140)
    private String raisonSociale;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_identifiant", length = 10)
    private CodeSystemeIdentification typeIdentifiant;

    @Column(name = "identifiant", length = 35)
    private String identifiant;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "nationalite", length = 2)
    private String nationalite;

    @Column(name = "pays", length = 2)
    private String pays;

    @Column(name = "adresse", length = 140)
    private String adresse;

    @Column(name = "ville", length = 140)
    private String ville;

    @Column(name = "code_postal", length = 20)
    private String codePostal;

    @Column(name = "telephone", length = 20)
    private String telephone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "numero_compte", length = 34)
    private String numeroCompte;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte", length = 10)
    private TypeCompte typeCompte;

    @Column(name = "code_membre_participant", length = 6)
    private String codeMembreParticipant;

    @Column(name = "code_marchand", length = 10)
    private String codeMarchand;

    @Column(name = "categorie_code_marchand", length = 10)
    private String categorieCodeMarchand;

    @Column(name = "nom_marchand", length = 140)
    private String nomMarchand;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private AliasStatus statut;

    @Column(name = "date_creation_rac")
    private LocalDateTime dateCreationRac;

    @Column(name = "date_modification_rac")
    private LocalDateTime dateModificationRac;

    @Column(name = "date_suppression_rac")
    private LocalDateTime dateSuppressionRac;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
