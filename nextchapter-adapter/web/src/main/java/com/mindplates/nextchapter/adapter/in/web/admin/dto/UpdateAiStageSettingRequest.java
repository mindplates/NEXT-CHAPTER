package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import com.mindplates.nextchapter.application.admin.port.in.command.UpdateAiStageSettingCommand;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAiStageSettingRequest(
        @NotNull(message = "벤더는 필수입니다.") AiVendor vendor,
        @NotBlank(message = "모델은 필수입니다.") @Size(max = 120) String model) {

    public UpdateAiStageSettingCommand toCommand(String actor) {
        return new UpdateAiStageSettingCommand(vendor, model, actor);
    }
}
