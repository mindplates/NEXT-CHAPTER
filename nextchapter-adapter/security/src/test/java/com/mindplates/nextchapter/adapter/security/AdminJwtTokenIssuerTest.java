package com.mindplates.nextchapter.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * 발급과 검증을 같은 설정으로 왕복시킨다.
 *
 * <p>이 어댑터에서 조용히 틀릴 수 있는 것은 <b>클레임 이름의 짝</b>이다. 발급이 {@code roles} 에 넣고
 * 디코더가 다른 이름을 읽으면 토큰은 유효한데 권한이 비어 403 이 되고, 원인이 인증처럼 보인다. 그래서
 * 두 쪽을 한 테스트에서 함께 검증한다.
 */
@DisplayName("관리자 JWT 발급·검증")
class AdminJwtTokenIssuerTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256";

    private final SecurityConfig config = new SecurityConfig();

    @Test
    @DisplayName("발급한 토큰이 같은 설정으로 검증된다")
    void issuedTokenIsVerifiable() {
        AdminTokenView token = issuer(SECRET).issue(account());

        Jwt decoded = decoder(SECRET).decode(token.accessToken());

        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(decoded.getSubject()).isEqualTo("admin@nextchapter.local");
        assertThat(decoded.getClaimAsStringList(AdminJwtTokenIssuer.ROLES_CLAIM))
                .containsExactly("ADMIN");
        assertThat(decoded.getClaim("adminId").toString()).isEqualTo("1");
    }

    @Test
    @DisplayName("roles 클레임이 ROLE_ 접두사가 붙은 권한으로 변환된다")
    void rolesBecomeGrantedAuthorities() {
        Jwt decoded = decoder(SECRET).decode(issuer(SECRET).issue(account()).accessToken());
        JwtAuthenticationConverter converter = config.rolesJwtAuthenticationConverter();

        var authentication = converter.convert(decoded);

        // Spring Security 7 은 Bearer 인증 사실을 FactorGrantedAuthority 로 함께 붙인다. 우리가 확인할
        // 것은 roles 클레임이 ROLE_ 권한으로 옮겨졌는지이므로 포함 여부만 본다.
        assertThat(authentication.getAuthorities()).extracting(Object::toString).contains(AdminRole.ADMIN.authority());
    }

    @Test
    @DisplayName("만료 시각이 설정된 TTL 을 따른다")
    void expiryFollowsTtl() {
        JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(30), null, "nextchapter");

        AdminTokenView token = new AdminJwtTokenIssuer(config.jwtEncoder(key(SECRET)), properties).issue(account());

        assertThat(token.expiresAt())
                .isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 검증되지 않는다")
    void rejectsTokenSignedWithAnotherKey() {
        String token = issuer(SECRET).issue(account()).accessToken();
        JwtDecoder otherDecoder = decoder("another-secret-that-is-long-enough-for-hs256");

        assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(Exception.class);
    }

    /** 설정이 비어 있으면 임의 키를 쓰므로, 두 인스턴스가 서로의 토큰을 검증할 수 없어야 한다. */
    @Test
    @DisplayName("키 설정이 없으면 부팅마다 다른 키가 쓰인다")
    void randomKeyWhenUnconfigured() {
        assertThat(JwtSecretKeyFactory.create(null).getEncoded())
                .isNotEqualTo(JwtSecretKeyFactory.create("").getEncoded());
    }

    @Test
    @DisplayName("32바이트보다 짧은 키는 거절한다 — HS256 이 요구하는 최소 길이다")
    void rejectsShortKey() {
        assertThatThrownBy(() -> JwtSecretKeyFactory.create("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("TTL·issuer 를 주지 않으면 기본값이 채워진다")
    void propertiesFillDefaults() {
        JwtProperties properties = new JwtProperties(SECRET, null, null, "  ");

        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(8));
        assertThat(properties.userTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.issuer()).isEqualTo("nextchapter");
    }

    private AdminJwtTokenIssuer issuer(String secret) {
        return new AdminJwtTokenIssuer(
                config.jwtEncoder(key(secret)), new JwtProperties(secret, Duration.ofHours(1), null, "nextchapter"));
    }

    private JwtDecoder decoder(String secret) {
        return config.jwtDecoder(key(secret));
    }

    private SecretKey key(String secret) {
        return config.jwtSecretKey(new JwtProperties(secret, Duration.ofHours(1), null, "nextchapter"));
    }

    private static AdminAccount account() {
        return new AdminAccount(
                1L, "admin@nextchapter.local", "관리자", "{bcrypt}hash", AdminRole.ADMIN, true, null, null, null);
    }
}
