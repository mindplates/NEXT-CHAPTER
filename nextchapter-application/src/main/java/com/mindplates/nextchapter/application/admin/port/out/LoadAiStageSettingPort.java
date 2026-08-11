package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiStageSetting;
import java.util.List;
import java.util.Optional;

public interface LoadAiStageSettingPort {

    List<AiStageSetting> findAll();

    Optional<AiStageSetting> findByStage(AiStage stage);
}
