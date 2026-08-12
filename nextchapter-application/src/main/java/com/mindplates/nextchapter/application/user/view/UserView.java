package com.mindplates.nextchapter.application.user.view;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.time.LocalDateTime;

/** 사용자 프로필 응답. {@code providerUserId} 는 나가지 않는다 — 밖에서 쓸 일이 없는 제공자 내부 값이다. */
public record UserView(Long id, SocialProvider provider, String email, String displayName, LocalDateTime lastLoginAt) {

    public static UserView from(User user) {
        return new UserView(user.id(), user.provider(), user.email(), user.displayName(), user.lastLoginAt());
    }
}
