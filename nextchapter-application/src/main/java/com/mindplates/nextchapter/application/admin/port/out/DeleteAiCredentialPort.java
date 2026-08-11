package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;

public interface DeleteAiCredentialPort {

    void deleteByVendor(AiVendor vendor);
}
