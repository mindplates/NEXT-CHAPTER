package com.mindplates.nextchapter.adapter.in.web.catalog;

import com.mindplates.nextchapter.adapter.in.web.catalog.dto.AddTopicAliasRequest;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.CatalogTopicResponse;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.CreateCatalogTopicRequest;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.TopicAliasResponse;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.UpdateCatalogNodeRequest;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.GetCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ManageTopicAliasUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogTopicUseCase;
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
public class CatalogTopicAdminController {

    private final GetCatalogUseCase getCatalogUseCase;
    private final CreateCatalogTopicUseCase createCatalogTopicUseCase;
    private final UpdateCatalogTopicUseCase updateCatalogTopicUseCase;
    private final DeleteCatalogTopicUseCase deleteCatalogTopicUseCase;
    private final ManageTopicAliasUseCase manageTopicAliasUseCase;

    @GetMapping("/fields/{fieldId}/topics")
    public ResponseEntity<ApiResponse<List<CatalogTopicResponse>>> list(@PathVariable Long fieldId) {
        List<CatalogTopicResponse> result = getCatalogUseCase.topics(fieldId).stream()
                .map(CatalogTopicResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/fields/{fieldId}/topics")
    public ResponseEntity<ApiResponse<CatalogTopicResponse>> create(
            @PathVariable Long fieldId, @RequestBody @Valid CreateCatalogTopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        CatalogTopicResponse.from(createCatalogTopicUseCase.create(request.toCommand(fieldId)))));
    }

    @GetMapping("/topics/{id}")
    public ResponseEntity<ApiResponse<CatalogTopicResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(CatalogTopicResponse.from(getCatalogUseCase.topic(id))));
    }

    @PutMapping("/topics/{id}")
    public ResponseEntity<ApiResponse<CatalogTopicResponse>> update(
            @PathVariable Long id, @RequestBody @Valid UpdateCatalogNodeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(CatalogTopicResponse.from(updateCatalogTopicUseCase.update(id, request.toCommand()))));
    }

    @DeleteMapping("/topics/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        deleteCatalogTopicUseCase.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/topics/{topicId}/aliases")
    public ResponseEntity<ApiResponse<List<TopicAliasResponse>>> aliases(@PathVariable Long topicId) {
        List<TopicAliasResponse> result = getCatalogUseCase.aliases(topicId).stream()
                .map(TopicAliasResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/topics/{topicId}/aliases")
    public ResponseEntity<ApiResponse<TopicAliasResponse>> addAlias(
            @PathVariable Long topicId, @RequestBody @Valid AddTopicAliasRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(TopicAliasResponse.from(manageTopicAliasUseCase.add(topicId, request.alias()))));
    }

    @DeleteMapping("/aliases/{aliasId}")
    public ResponseEntity<ApiResponse<Void>> removeAlias(@PathVariable Long aliasId) {
        manageTopicAliasUseCase.remove(aliasId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
