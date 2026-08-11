package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import java.util.List;

public interface GetAdminAccountUseCase {

    List<AdminAccount> list();

    AdminAccount getByEmail(String email);
}
