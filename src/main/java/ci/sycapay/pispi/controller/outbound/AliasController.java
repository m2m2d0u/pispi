package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.alias.AliasCreationRequest;
import ci.sycapay.pispi.dto.alias.AliasResponse;
import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.enums.CodeRaisonSuppression;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.service.alias.AliasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

@Tag(name = "Aliases")
@Slf4j
@RestController
@RequestMapping("/api/v1/aliases")
@RequiredArgsConstructor
public class AliasController {

    private final AliasService aliasService;

    @Operation(summary = "Create an alias", description = "Registers a new account alias in the RAC via the AIP. Idempotent: duplicate alias+type combinations are rejected by the AIP.")
    @PostMapping
    public ResponseEntity<ApiResponse<AliasResponse>> createAlias(@Valid @RequestBody AliasCreationRequest request) {
        AliasResponse data = aliasService.createAlias(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(data));
    }

    @Operation(summary = "Modify an alias", description = "Updates account or client information linked to an existing active alias in the RAC.")
    @PutMapping
    public ApiResponse<AliasResponse> modifyAlias(@Valid @RequestBody AliasCreationRequest request) {
        return ApiResponse.ok(aliasService.modifyAlias(request));
    }

    @Operation(summary = "Delete an alias", description = "Removes an alias from the RAC and marks it as DELETED locally.")
    @DeleteMapping("/{typeAlias}/{aliasValue}")
    public ApiResponse<AliasResponse> deleteAlias(
            @Parameter(description = "Type of alias") @PathVariable TypeAlias typeAlias,
            @Parameter(description = "The alias value to delete") @PathVariable String aliasValue,
            @Parameter(description = "Reason for deletion: FERMETURE_COMPTE_CLIENT or DEMANDE_CLIENT") @RequestParam CodeRaisonSuppression raisonSuppression) {
        return ApiResponse.ok(aliasService.deleteAlias(typeAlias, aliasValue, raisonSuppression));
    }

    @Operation(summary = "Search an alias in the RAC", description = "Queries the AIP RAC for the account linked to a given alias. Returns the raw AIP response including account number, type, and client info.")
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> searchAlias(
            @Parameter(description = "Type of alias") @RequestParam TypeAlias typeAlias,
            @Parameter(description = "The alias value to search") @RequestParam String alias) {
        return ApiResponse.ok(aliasService.searchAlias(typeAlias, alias));
    }

    @Operation(summary = "List local active aliases", description = "Returns a paginated list of all ACTIVE aliases registered by this participant, stored in the local database.")
    @GetMapping
    public ApiResponse<Page<AliasResponse>> listAliases(Pageable pageable) {
        return ApiResponse.ok(aliasService.listAliases(pageable));
    }
}
