package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiCredential;

public interface SaveAiCredentialPort {

    AiCredential save(AiCredential credential);
}
