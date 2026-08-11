package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogFieldCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCatalogFieldRequest(
        @NotBlank(message = "slug 은 필수입니다.") @Size(max = 80) String slug,
        @NotBlank(message = "이름은 필수입니다.") @Size(max = 120) String name,
        String description,
        Integer sortOrder) {

    public CreateCatalogFieldCommand toCommand(Long domainId) {
        return new CreateCatalogFieldCommand(domainId, slug, name, description, sortOrder == null ? 0 : sortOrder);
    }
}
