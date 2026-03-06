package ci.sycapay.pispi.dto.common;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantInfo {

    @Size(max = 10)
    private String codeMarchand;

    @Size(max = 10)
    private String categorieCodeMarchand;

    @Size(max = 140)
    private String nomMarchand;

    @Size(max = 140)
    private String villeMarchand;

    @Size(max = 2)
    private String paysMarchand;
}
