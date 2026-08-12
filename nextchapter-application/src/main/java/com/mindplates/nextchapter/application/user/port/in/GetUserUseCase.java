package com.mindplates.nextchapter.application.user.port.in;

import com.mindplates.nextchapter.application.user.view.UserView;

public interface GetUserUseCase {

    UserView getById(Long id);
}
