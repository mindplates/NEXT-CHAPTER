package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import java.time.LocalDateTime;

public record CatalogDomainResponse(
        Long id,
        String slug,
        String name,
        String description,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CatalogDomainResponse from(CatalogDomain domain) {
        return new CatalogDomainResponse(
                domain.id(),
                domain.slug(),
                domain.name(),
                domain.description(),
                domain.sortOrder(),
                domain.createdAt(),
                domain.updatedAt());
    }
}
