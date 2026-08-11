package com.mindplates.nextchapter.adapter.in.web.catalog;

import com.mindplates.nextchapter.adapter.in.web.catalog.dto.CatalogFieldResponse;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.CreateCatalogFieldRequest;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.UpdateCatalogNodeRequest;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.GetCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogFieldUseCase;
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
public class CatalogFieldAdminController {

    private final GetCatalogUseCase getCatalogUseCase;
    private final CreateCatalogFieldUseCase createCatalogFieldUseCase;
    private final UpdateCatalogFieldUseCase updateCatalogFieldUseCase;
    private final DeleteCatalogFieldUseCase deleteCatalogFieldUseCase;

    @GetMapping("/domains/{domainId}/fields")
    public ResponseEntity<ApiResponse<List<CatalogFieldResponse>>> list(@PathVariable Long domainId) {
        List<CatalogFieldResponse> result = getCatalogUseCase.fields(domainId).stream()
                .map(CatalogFieldResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/domains/{domainId}/fields")
    public ResponseEntity<ApiResponse<CatalogFieldResponse>> create(
            @PathVariable Long domainId, @RequestBody @Valid CreateCatalogFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        CatalogFieldResponse.from(createCatalogFieldUseCase.create(request.toCommand(domainId)))));
    }

    @GetMapping("/fields/{id}")
    public ResponseEntity<ApiResponse<CatalogFieldResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(CatalogFieldResponse.from(getCatalogUseCase.field(id))));
    }

    @PutMapping("/fields/{id}")
    public ResponseEntity<ApiResponse<CatalogFieldResponse>> update(
            @PathVariable Long id, @RequestBody @Valid UpdateCatalogNodeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(CatalogFieldResponse.from(updateCatalogFieldUseCase.update(id, request.toCommand()))));
    }

    @DeleteMapping("/fields/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        deleteCatalogFieldUseCase.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
