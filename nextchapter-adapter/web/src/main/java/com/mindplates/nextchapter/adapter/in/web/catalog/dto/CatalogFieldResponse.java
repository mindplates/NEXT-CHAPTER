package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import java.time.LocalDateTime;

public record CatalogFieldResponse(
        Long id,
        Long domainId,
        String slug,
        String name,
        String description,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CatalogFieldResponse from(CatalogField field) {
        return new CatalogFieldResponse(
                field.id(),
                field.domainId(),
                field.slug(),
                field.name(),
                field.description(),
                field.sortOrder(),
                field.createdAt(),
                field.updatedAt());
    }
}
