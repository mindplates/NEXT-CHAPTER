package com.mindplates.nextchapter.application.user.port.out;

import com.mindplates.nextchapter.domain.user.model.User;

public interface SaveUserPort {

    User save(User user);
}
