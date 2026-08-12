package com.mindplates.nextchapter.adapter.security.social;

import com.mindplates.nextchapter.application.user.port.out.SocialProfile;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Google OpenID Connect.
 *
 * <p>정체성은 {@code sub} 다. {@code email} 을 쓰지 않는 이유는 사용자가 Google 쪽에서 이메일을 바꿀 수
 * 있고, 그러면 같은 사람이 새 계정을 받아 학습 레이어가 끊기기 때문이다.
 */
@Component
class GoogleSocialProfileClient extends AbstractSocialProfileClient {

    GoogleSocialProfileClient(SocialAuthProperties properties) {
        super(properties);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    protected SocialProfile toProfile(Map<String, Object> userInfo) {
        return new SocialProfile(
                SocialProvider.GOOGLE,
                requireIdentity(text(userInfo, "sub")),
                text(userInfo, "email"),
                text(userInfo, "name"));
    }
}
