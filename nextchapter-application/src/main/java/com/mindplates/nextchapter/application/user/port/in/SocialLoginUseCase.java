package com.mindplates.nextchapter.application.user.port.in;

import com.mindplates.nextchapter.application.user.port.in.command.SocialLoginCommand;
import com.mindplates.nextchapter.application.user.view.UserTokenView;

public interface SocialLoginUseCase {

    UserTokenView login(SocialLoginCommand command);
}
