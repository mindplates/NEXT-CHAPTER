package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogDomainCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCatalogDomainRequest(
        @NotBlank(message = "slug 은 필수입니다.") @Size(max = 80) String slug,
        @NotBlank(message = "이름은 필수입니다.") @Size(max = 120) String name,
        String description,
        Integer sortOrder) {

    public CreateCatalogDomainCommand toCommand() {
        return new CreateCatalogDomainCommand(slug, name, description, sortOrder == null ? 0 : sortOrder);
    }
}
