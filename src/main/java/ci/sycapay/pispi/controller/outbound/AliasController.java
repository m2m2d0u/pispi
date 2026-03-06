package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.alias.AliasCreationRequest;
import ci.sycapay.pispi.dto.alias.AliasResponse;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.service.alias.AliasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/aliases")
@RequiredArgsConstructor
public class AliasController {

    private final AliasService aliasService;

    @PostMapping
    public AliasResponse createAlias(@Valid @RequestBody AliasCreationRequest request) {
        return aliasService.createAlias(request);
    }

    @PutMapping
    public AliasResponse modifyAlias(@Valid @RequestBody AliasCreationRequest request) {
        return aliasService.modifyAlias(request);
    }

    @DeleteMapping("/{typeAlias}/{aliasValue}")
    public AliasResponse deleteAlias(@PathVariable TypeAlias typeAlias, @PathVariable String aliasValue) {
        return aliasService.deleteAlias(typeAlias, aliasValue);
    }

    @GetMapping("/search")
    public Map<String, Object> searchAlias(@RequestParam TypeAlias typeAlias, @RequestParam String alias) {
        return aliasService.searchAlias(typeAlias, alias);
    }

    @GetMapping
    public Page<AliasResponse> listAliases(Pageable pageable) {
        return aliasService.listAliases(pageable);
    }
}
