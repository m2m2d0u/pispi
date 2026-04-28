package ci.sycapay.pispi.dto.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload pour rejeter un PACS.008 entrant.
 *
 * <p>BCEAO {@code Reponse de transfert} (PACS.002) : quand le PI payé refuse
 * un PACS.008 reçu (compte introuvable, provision insuffisante, fraude…), on
 * émet un PACS.002 avec {@code statutTransaction=RJCT} et un {@code codeRaison}
 * conforme au pattern {@code [A-Z]{2}\d{2}}.
 *
 * <p>Codes BCEAO usuels :
 * <ul>
 *   <li>{@code AC01} — Compte du créditeur introuvable</li>
 *   <li>{@code AC04} — Compte du créditeur clôturé</li>
 *   <li>{@code AM04} — Provision insuffisante côté payeur</li>
 *   <li>{@code BE01} — Identité du bénéficiaire incohérente</li>
 *   <li>{@code FR01} — Suspicion de fraude</li>
 *   <li>{@code MS03} — Raison non spécifiée</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomingTransferRejectCommand {

    @NotBlank
    @Size(max = 4)
    @Pattern(regexp = "^[A-Z]{2}\\d{2}$",
            message = "codeRaison doit suivre le pattern BCEAO [A-Z]{2}\\d{2} (ex: AC01, AM04, FR01)")
    private String codeRaison;
}
