package ci.sycapay.pispi.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de demande de rapport (CAMT.060). Aligné sur le schéma BCEAO
 * {@code DemandeRapport} (cf. interface-participant-openapi.json) qui impose
 * deux patterns stricts :
 *
 * <ul>
 *   <li>{@code dateDebutPeriode} : {@code YYYY-MM-DD}</li>
 *   <li>{@code heureDebutPeriode} : {@code HH:mm:ss.SSSZ} (millisecondes
 *       + suffixe Z requis). Sans le {@code .SSS} ou sans le {@code Z}
 *       l'AIP rejette en 400 « heureDebutPeriode est invalide ».</li>
 * </ul>
 *
 * <p>On valide en DTO (Bean Validation) pour échouer en 400 propre côté
 * client avant l'aller-retour AIP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {

    @NotBlank
    @Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
            message = "dateDebutPeriode doit être au format YYYY-MM-DD (ex. 2026-05-01)"
    )
    private String dateDebutPeriode;

    @NotBlank
    @Pattern(
            regexp = "^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$",
            message = "heureDebutPeriode doit être au format HH:mm:ss.SSSZ "
                    + "(millisecondes + suffixe Z requis par BCEAO §4.9, "
                    + "ex. 00:00:00.000Z)"
    )
    private String heureDebutPeriode;
}
