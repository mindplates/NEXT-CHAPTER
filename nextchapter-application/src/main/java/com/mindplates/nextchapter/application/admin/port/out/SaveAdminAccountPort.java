package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AdminAccount;

public interface SaveAdminAccountPort {

    AdminAccount save(AdminAccount account);
}
