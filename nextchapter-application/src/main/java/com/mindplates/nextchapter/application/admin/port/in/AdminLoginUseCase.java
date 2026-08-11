package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.port.in.command.AdminLoginCommand;
import com.mindplates.nextchapter.application.admin.view.AdminTokenView;

public interface AdminLoginUseCase {

    AdminTokenView login(AdminLoginCommand command);
}
