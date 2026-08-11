package com.mindplates.nextchapter.application.admin.port.in.command;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;

public record UpdateAiStageSettingCommand(AiVendor vendor, String model, String actor) {}
