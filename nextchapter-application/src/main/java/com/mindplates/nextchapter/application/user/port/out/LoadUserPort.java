package com.mindplates.nextchapter.application.user.port.out;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.util.Optional;

public interface LoadUserPort {

    Optional<User> findById(Long id);

    /** 정체성 조회. 이메일이 아니라 (제공자, 제공자 식별자)로 찾는다. */
    Optional<User> findByProviderIdentity(SocialProvider provider, String providerUserId);
}
