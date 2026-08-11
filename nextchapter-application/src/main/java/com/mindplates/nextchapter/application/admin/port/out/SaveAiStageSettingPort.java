package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiStageSetting;

public interface SaveAiStageSettingPort {

    AiStageSetting save(AiStageSetting setting);
}
