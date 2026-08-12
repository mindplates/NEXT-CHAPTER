package com.mindplates.nextchapter.adapter.security.social;

import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 제공자 등록 정보.
 *
 * <p>클라이언트 시크릿에 <b>기본값을 두지 않는다.</b> JWT 서명 키와 같은 이유다 — 코드에 고정값이
 * 들어가면 배포본 전체가 같은 값을 쓰고, 그건 아무 증상 없이 틀린다. 값이 없으면 그 제공자의
 * 로그인만 실패하고 나머지는 정상 동작한다.
 *
 * <p>엔드포인트 주소까지 설정으로 뺀 이유는 두 가지다. 제공자가 주소를 바꿀 때 재배포가 아니라 설정
 * 변경으로 끝나고, 테스트가 로컬 스텁 서버를 가리킬 수 있다 — 주소를 코드에 박으면 이 어댑터에서
 * 정작 틀릴 수 있는 것(헤더 이름·JSON 필드 이름)이 테스트에서 빠진다.
 */
@ConfigurationProperties(prefix = "nextchapter.auth.social")
public record SocialAuthProperties(
        Map<SocialProvider, Registration> providers, Duration connectTimeout, Duration readTimeout) {

    public SocialAuthProperties {
        // EnumMap 의 복사 생성자는 빈 일반 Map 을 거절한다 — 설정이 아예 없는 것이 정상 상태이므로
        // (제공자 하나만 등록해 두는 배포가 있다) 넣어서 만든다.
        Map<SocialProvider, Registration> copy = new EnumMap<>(SocialProvider.class);
        if (providers != null) {
            copy.putAll(providers);
        }
        providers = copy;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }

    /**
     * 설정이 없거나 불완전하면 <b>즉시</b> 거절한다. 폴백하지 않는 이유는 AI 프로바이더 설정과 같다 —
     * 조용히 다른 값으로 넘어가면 로그인은 성공하는데 엉뚱한 계정에 붙는다.
     */
    public Registration require(SocialProvider provider) {
        Registration registration = providers.get(provider);
        if (registration == null || !registration.isConfigured()) {
            throw new InvalidOperationException(provider + " 소셜 로그인이 설정되지 않았습니다.");
        }
        return registration;
    }

    public boolean isConfigured(SocialProvider provider) {
        Registration registration = providers.get(provider);
        return registration != null && registration.isConfigured();
    }

    /** @param clientSecret 제공자에 따라 없을 수 있다(Kakao 는 선택). 그래서 필수 항목에 넣지 않는다 */
    public record Registration(String clientId, String clientSecret, String tokenUri, String userInfoUri) {

        public boolean isConfigured() {
            return hasText(clientId) && hasText(tokenUri) && hasText(userInfoUri);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
