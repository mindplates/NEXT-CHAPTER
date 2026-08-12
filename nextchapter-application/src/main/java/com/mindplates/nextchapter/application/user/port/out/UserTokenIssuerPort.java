package com.mindplates.nextchapter.application.user.port.out;

import com.mindplates.nextchapter.application.user.view.UserTokenView;
import com.mindplates.nextchapter.domain.user.model.User;

public interface UserTokenIssuerPort {

    UserTokenView issue(User user);
}
