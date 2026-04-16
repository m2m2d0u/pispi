package ci.sycapay.pispi.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Merchant information for MCOD alias creation.
 *
 * Per BCEAO OpenAPI spec:
 * - codeMarchand: 2-10 digits
 * - categorieCodeMarchand: MCC code (4 digits)
 * - nomMarchand maps to denominationSociale
 * - categorieCodeMarchand maps to codeActivite
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantInfo {

    /**
     * Code marchand (2-10 chiffres).
     * Identifiant unique du marchand attribué par le participant.
     */
    @NotBlank(message = "codeMarchand is required")
    @Size(max = 10, message = "codeMarchand must not exceed 10 characters")
    @Pattern(regexp = "^\\d{2,10}$", message = "codeMarchand must be 2-10 digits")
    private String codeMarchand;

    /**
     * Catégorie du code marchand (MCC - Merchant Category Code).
     * Code d'activité à 4 chiffres selon la norme ISO 18245.
     */
    @NotBlank(message = "categorieCodeMarchand is required")
    @Size(max = 10, message = "categorieCodeMarchand must not exceed 10 characters")
    @Pattern(regexp = "^\\d{4}$", message = "categorieCodeMarchand must be 4 digits (MCC code)")
    private String categorieCodeMarchand;

    /**
     * Nom commercial du marchand.
     * Mappé vers denominationSociale dans l'API BCEAO.
     */
    @NotBlank(message = "nomMarchand is required")
    @Size(max = 140, message = "nomMarchand must not exceed 140 characters")
    private String nomMarchand;

    /** Ville du marchand. */
    @Size(max = 140, message = "villeMarchand must not exceed 140 characters")
    private String villeMarchand;

    /**
     * Pays du marchand au format ISO 3166 alpha-2.
     * Doit être un pays UEMOA.
     */
    @Size(min = 2, max = 2, message = "paysMarchand must be ISO 3166 alpha-2 code (2 characters)")
    @Pattern(regexp = "^(CI|SN|ML|BF|NE|BJ|TG|GW)$",
             message = "paysMarchand must be a UEMOA country code: CI, SN, ML, BF, NE, BJ, TG, or GW")
    private String paysMarchand;
}
