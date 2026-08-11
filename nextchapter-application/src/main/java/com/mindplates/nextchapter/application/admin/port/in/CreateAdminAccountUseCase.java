package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.port.in.command.CreateAdminAccountCommand;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;

public interface CreateAdminAccountUseCase {

    AdminAccount create(CreateAdminAccountCommand command);
}
