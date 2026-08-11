package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiUsageRecord;

public interface SaveAiUsagePort {

    AiUsageRecord record(AiUsageRecord usage);
}
