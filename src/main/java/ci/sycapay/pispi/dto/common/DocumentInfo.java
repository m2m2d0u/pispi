package ci.sycapay.pispi.dto.common;

import ci.sycapay.pispi.enums.CodeTypeDocument;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {

    private CodeTypeDocument codeTypeDocument;

    @Size(max = 35)
    private String identifiantDocument;

    @Size(max = 140)
    private String libelleDocument;
}
