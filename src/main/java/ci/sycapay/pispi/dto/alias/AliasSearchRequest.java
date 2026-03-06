package ci.sycapay.pispi.dto.alias;

import ci.sycapay.pispi.enums.TypeAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliasSearchRequest {

    @NotBlank
    private String alias;

    @NotNull
    private TypeAlias typeAlias;
}
