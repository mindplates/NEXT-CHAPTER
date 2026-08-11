package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiCredential;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.List;
import java.util.Optional;

public interface LoadAiCredentialPort {

    List<AiCredential> findAll();

    Optional<AiCredential> findByVendor(AiVendor vendor);
}
