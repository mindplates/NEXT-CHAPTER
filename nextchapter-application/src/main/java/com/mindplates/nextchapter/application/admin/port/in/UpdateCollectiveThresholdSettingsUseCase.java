package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.view.CollectiveThresholdView;

public interface UpdateCollectiveThresholdSettingsUseCase {

    CollectiveThresholdView update(int questionThreshold, int minAttempts, int wrongRatePercent, String actor);
}
