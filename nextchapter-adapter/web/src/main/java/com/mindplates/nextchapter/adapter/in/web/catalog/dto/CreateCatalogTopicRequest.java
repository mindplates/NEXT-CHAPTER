package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogTopicCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCatalogTopicRequest(
        @NotBlank(message = "slug 은 필수입니다.") @Size(max = 120) String slug,
        @NotBlank(message = "이름은 필수입니다.") @Size(max = 160) String name,
        String description,
        Integer sortOrder) {

    public CreateCatalogTopicCommand toCommand(Long fieldId) {
        return new CreateCatalogTopicCommand(fieldId, slug, name, description, sortOrder == null ? 0 : sortOrder);
    }
}
