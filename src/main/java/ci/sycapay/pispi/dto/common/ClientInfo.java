package ci.sycapay.pispi.dto.common;

import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.TypeClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientInfo {

    @NotBlank @Size(max = 140)
    private String nom;

    @Size(max = 140)
    private String prenom;

    @Size(max = 140)
    private String autrePrenom;

    @Size(max = 140)
    private String raisonSociale;

    @Size(max = 35)
    private String denominationSociale;

    /** Genre: "1" = Masculin, "2" = Féminin. Obligatoire pour PP et C. */
    private String genre;

    @NotNull
    private TypeClient typeClient;

    @NotNull
    private CodeSystemeIdentification typeIdentifiant;

    @NotBlank @Size(max = 35)
    private String identifiant;

    private String dateNaissance;

    @Size(max = 140)
    private String lieuNaissance;

    /** Pays de naissance au format ISO 3166 alpha-2. Obligatoire pour PP de participant de type banque avec typeCompte ≠ TRAL. */
    @Size(max = 2)
    private String paysNaissance;

    @Size(max = 2)
    private String nationalite;

    @Size(max = 140)
    private String adresse;

    @Size(max = 140)
    private String ville;

    @Size(max = 2)
    private String pays;

    @NotBlank @Size(max = 20)
    private String telephone;

    @Email @Size(max = 254)
    private String email;

    @Size(max = 20)
    private String codePostal;
}
