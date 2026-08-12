package com.mindplates.nextchapter.adapter.security.social;

import com.mindplates.nextchapter.application.user.port.out.SocialProfile;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Kakao.
 *
 * <p>응답 모양이 Google 과 다른 것이 제공자별 어댑터가 필요한 이유다 — 정체성이 {@code id}(숫자)이고
 * 이메일과 닉네임은 {@code kakao_account} 안에 두 겹으로 들어 있다. 이메일은 <b>동의 항목</b>이라
 * 없을 수 있고, 그때 로그인을 실패시키면 사용자는 원인을 알 수 없다.
 */
@Component
class KakaoSocialProfileClient extends AbstractSocialProfileClient {

    KakaoSocialProfileClient(SocialAuthProperties properties) {
        super(properties);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    protected SocialProfile toProfile(Map<String, Object> userInfo) {
        Map<String, Object> account = nested(userInfo, "kakao_account");
        Map<String, Object> profile = nested(account, "profile");
        return new SocialProfile(
                SocialProvider.KAKAO,
                requireIdentity(text(userInfo, "id")),
                text(account, "email"),
                text(profile, "nickname"));
    }
}
