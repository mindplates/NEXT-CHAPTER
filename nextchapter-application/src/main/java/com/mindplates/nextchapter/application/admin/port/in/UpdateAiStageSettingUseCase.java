package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.port.in.command.UpdateAiStageSettingCommand;
import com.mindplates.nextchapter.application.admin.view.AiStageSettingView;
import com.mindplates.nextchapter.domain.admin.model.AiStage;

public interface UpdateAiStageSettingUseCase {

    AiStageSettingView update(AiStage stage, UpdateAiStageSettingCommand command);
}
