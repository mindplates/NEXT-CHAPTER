package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.view.AiCredentialView;
import com.mindplates.nextchapter.application.admin.view.AiStageSettingView;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.List;

public interface GetAiSettingsUseCase {

    List<AiStageSettingView> stageSettings();

    List<AiCredentialView> credentials();

    /** 어댑터가 등록돼 실제로 호출 가능한 벤더. 관리 화면의 선택지가 이 목록이어야 한다. */
    List<AiVendor> supportedVendors();
}
