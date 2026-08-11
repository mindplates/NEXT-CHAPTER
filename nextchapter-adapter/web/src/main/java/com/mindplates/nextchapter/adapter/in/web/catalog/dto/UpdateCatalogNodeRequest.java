package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.application.catalog.port.in.command.UpdateCatalogNodeCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 세 계층 공통 수정 요청. slug 는 안정 키라 여기에 없다. */
public record UpdateCatalogNodeRequest(
        @NotBlank(message = "이름은 필수입니다.") @Size(max = 160) String name, String description, Integer sortOrder) {

    public UpdateCatalogNodeCommand toCommand() {
        return new UpdateCatalogNodeCommand(name, description, sortOrder == null ? 0 : sortOrder);
    }
}
