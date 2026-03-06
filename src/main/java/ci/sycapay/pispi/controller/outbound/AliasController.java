package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.alias.AliasCreationRequest;
import ci.sycapay.pispi.dto.alias.AliasResponse;
import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.service.alias.AliasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/aliases")
@RequiredArgsConstructor
public class AliasController {

    private final AliasService aliasService;

    @PostMapping
    public ResponseEntity<ApiResponse<AliasResponse>> createAlias(@Valid @RequestBody AliasCreationRequest request) {
        AliasResponse data = aliasService.createAlias(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(data));
    }

    @PutMapping
    public ApiResponse<AliasResponse> modifyAlias(@Valid @RequestBody AliasCreationRequest request) {
        return ApiResponse.ok(aliasService.modifyAlias(request));
    }

    @DeleteMapping("/{typeAlias}/{aliasValue}")
    public ApiResponse<AliasResponse> deleteAlias(@PathVariable TypeAlias typeAlias, @PathVariable String aliasValue) {
        return ApiResponse.ok(aliasService.deleteAlias(typeAlias, aliasValue));
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> searchAlias(@RequestParam TypeAlias typeAlias, @RequestParam String alias) {
        return ApiResponse.ok(aliasService.searchAlias(typeAlias, alias));
    }

    @GetMapping
    public ApiResponse<Page<AliasResponse>> listAliases(Pageable pageable) {
        return ApiResponse.ok(aliasService.listAliases(pageable));
    }
}
