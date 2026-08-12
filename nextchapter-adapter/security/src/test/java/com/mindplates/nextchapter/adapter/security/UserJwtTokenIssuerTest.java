package com.mindplates.nextchapter.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.application.user.view.UserTokenView;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 사용자 토큰이 관리자 토큰과 <b>구분되는지</b>를 본다. 같은 키로 서명하므로 역할 클레임이 유일한
 * 구분선이고, 그것이 빠지면 사용자 토큰으로 승인 대기열에 접근할 수 있다.
 */
@DisplayName("사용자 JWT 발급·검증")
class UserJwtTokenIssuerTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256";

    private final SecurityConfig config = new SecurityConfig();

    /** subject 가 사용자 ID 여야 한다. 이메일이면 제공자가 이메일을 안 준 사용자의 토큰이 만들어지지 않는다. */
    @Test
    @DisplayName("subject 는 사용자 ID 이고 역할은 USER 다")
    void subjectIsUserId() {
        UserTokenView token = issuer(Duration.ofDays(7)).issue(user());

        Jwt decoded = config.jwtDecoder(key()).decode(token.accessToken());

        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsStringList(AdminJwtTokenIssuer.ROLES_CLAIM))
                .containsExactly("USER");
        assertThat(decoded.getClaimAsString("provider")).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("응답에 프로필이 실린다 — 로그인 직후 추가 왕복이 없다")
    void carriesProfile() {
        UserTokenView token = issuer(Duration.ofDays(7)).issue(user());

        assertThat(token.user().id()).isEqualTo(42L);
        assertThat(token.user().displayName()).isEqualTo("학습자1");
        assertThat(token.user().provider()).isEqualTo(SocialProvider.GOOGLE);
    }

    /** 관리자 TTL 을 쓰면 학습 중 8시간마다 다시 로그인해야 한다 — 별도 값인 것이 요점이다. */
    @Test
    @DisplayName("만료는 사용자 TTL 을 따른다 (관리자 TTL 이 아니다)")
    void expiryFollowsUserTtl() {
        UserTokenView token = issuer(Duration.ofDays(7)).issue(user());

        assertThat(token.expiresAt())
                .isBetween(Instant.now().plus(Duration.ofDays(6)), Instant.now().plus(Duration.ofDays(8)));
    }

    private UserJwtTokenIssuer issuer(Duration userTtl) {
        return new UserJwtTokenIssuer(config.jwtEncoder(key()), properties(userTtl));
    }

    private SecretKey key() {
        return config.jwtSecretKey(properties(Duration.ofDays(7)));
    }

    private static JwtProperties properties(Duration userTtl) {
        return new JwtProperties(SECRET, Duration.ofHours(1), userTtl, "nextchapter");
    }

    private static User user() {
        return new User(42L, SocialProvider.GOOGLE, "sub-1", "learner@example.com", "학습자1", null, null, null);
    }
}
