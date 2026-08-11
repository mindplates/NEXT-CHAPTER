package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.port.in.command.RegisterAiCredentialCommand;
import com.mindplates.nextchapter.application.admin.view.AiCredentialView;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;

public interface ManageAiCredentialUseCase {

    /** 등록과 교체가 같은 연산이다 — 벤더당 키는 하나이고, 교체는 덮어쓰기다. */
    AiCredentialView register(RegisterAiCredentialCommand command);

    void remove(AiVendor vendor);
}
