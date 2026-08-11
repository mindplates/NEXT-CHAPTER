package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiBudgetSettings;

public interface SaveAiBudgetSettingsPort {

    AiBudgetSettings save(AiBudgetSettings settings);
}
