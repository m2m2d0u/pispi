package ci.sycapay.pispi.dto.transfer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload pour répondre à une demande de vérification d'identité entrante
 * (ACMT.024 — schéma BCEAO {@code IdentiteReponse}).
 *
 * <p><b>Modèle simplifié.</b> Le caller fournit uniquement :
 * <ul>
 *   <li>{@code resultatVerification} — true (identité confirmée) ou false (rejet).</li>
 *   <li>{@code endToEndSearch} — l'{@code endToEndId} d'une RAC_SEARCH déjà
 *       effectuée sur le client à vérifier. Obligatoire quand
 *       {@code resultatVerification = true}. Le service résout TOUS les champs
 *       d'identité (nom, IBAN/Othr, type, ville, adresse, identification,
 *       naissance, pays…) depuis le résultat de cette recherche d'alias.</li>
 *   <li>{@code codeRaison} — utilisé en cas de rejet ({@code resultatVerification = false}).
 *       BCEAO pattern : {@code AC01}.</li>
 * </ul>
 *
 * <p>Pas de champs identité explicites : si tu n'as pas l'{@code endToEndSearch}
 * d'une RAC_SEARCH préalable, fais-en une via {@code POST /api/v1/aliases/search}
 * avant d'appeler ce respond.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRespondRequest {

    @NotNull
    private Boolean resultatVerification;

    /**
     * endToEndId d'une RAC_SEARCH inbound déjà journalisée pour le client à
     * vérifier. Obligatoire quand {@code resultatVerification = true} : le
     * service y récupère toute l'identité du client (nom, compte, type,
     * adresse, identifications, naissance) sans demander de saisie redondante
     * au caller. Ignoré quand {@code resultatVerification = false} (un rejet
     * n'a pas besoin de l'identité).
     */
    @Size(max = 35)
    private String endToEndSearch;

    /** Code ISO de rejet — obligatoire si {@code resultatVerification = false}. BCEAO pattern : {@code AC01}. */
    @Pattern(regexp = "^AC01$", message = "codeRaison must be AC01 per BCEAO IdentiteReponse schema")
    private String codeRaison;

    // -----------------------------------------------------------------------
    // Cross-field validations
    // -----------------------------------------------------------------------

    @JsonIgnore
    @AssertTrue(message = "endToEndSearch est obligatoire lorsque resultatVerification = true "
            + "(le service y résout l'identité complète du client). "
            + "Effectuer une RAC_SEARCH au préalable et fournir son endToEndId ici.")
    public boolean isEndToEndSearchProvidedOnSuccess() {
        if (!Boolean.TRUE.equals(resultatVerification)) return true;
        return endToEndSearch != null && !endToEndSearch.isBlank();
    }
}
