package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import java.util.List;
import java.util.Optional;

public interface LoadAdminAccountPort {

    List<AdminAccount> findAll();

    Optional<AdminAccount> findById(Long id);

    Optional<AdminAccount> findByEmail(String email);

    long count();
}
