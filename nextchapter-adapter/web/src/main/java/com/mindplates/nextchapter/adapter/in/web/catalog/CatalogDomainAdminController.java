package com.mindplates.nextchapter.adapter.in.web.catalog;

import com.mindplates.nextchapter.adapter.in.web.catalog.dto.CatalogDomainResponse;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.CreateCatalogDomainRequest;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.UpdateCatalogNodeRequest;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.GetCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.view.CatalogTreeView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
public class CatalogDomainAdminController {

    private final GetCatalogUseCase getCatalogUseCase;
    private final CreateCatalogDomainUseCase createCatalogDomainUseCase;
    private final UpdateCatalogDomainUseCase updateCatalogDomainUseCase;
    private final DeleteCatalogDomainUseCase deleteCatalogDomainUseCase;

    /** 3단계 전체를 한 응답으로. 관리 화면 트리와 매핑 프롬프트가 같은 모양을 필요로 한다. */
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<CatalogTreeView>> tree() {
        return ResponseEntity.ok(ApiResponse.ok(getCatalogUseCase.tree()));
    }

    @GetMapping("/domains")
    public ResponseEntity<ApiResponse<List<CatalogDomainResponse>>> list() {
        List<CatalogDomainResponse> result = getCatalogUseCase.domains().stream()
                .map(CatalogDomainResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/domains/{id}")
    public ResponseEntity<ApiResponse<CatalogDomainResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(CatalogDomainResponse.from(getCatalogUseCase.domain(id))));
    }

    @PostMapping("/domains")
    public ResponseEntity<ApiResponse<CatalogDomainResponse>> create(
            @RequestBody @Valid CreateCatalogDomainRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        CatalogDomainResponse.from(createCatalogDomainUseCase.create(request.toCommand()))));
    }

    @PutMapping("/domains/{id}")
    public ResponseEntity<ApiResponse<CatalogDomainResponse>> update(
            @PathVariable Long id, @RequestBody @Valid UpdateCatalogNodeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(CatalogDomainResponse.from(updateCatalogDomainUseCase.update(id, request.toCommand()))));
    }

    @DeleteMapping("/domains/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        deleteCatalogDomainUseCase.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
