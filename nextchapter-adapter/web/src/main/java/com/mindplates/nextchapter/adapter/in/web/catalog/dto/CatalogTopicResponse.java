package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import java.time.LocalDateTime;

public record CatalogTopicResponse(
        Long id,
        Long fieldId,
        String slug,
        String name,
        String description,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CatalogTopicResponse from(CatalogTopic topic) {
        return new CatalogTopicResponse(
                topic.id(),
                topic.fieldId(),
                topic.slug(),
                topic.name(),
                topic.description(),
                topic.sortOrder(),
                topic.createdAt(),
                topic.updatedAt());
    }
}
