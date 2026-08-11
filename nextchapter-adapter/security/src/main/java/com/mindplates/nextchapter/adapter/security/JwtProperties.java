package com.mindplates.nextchapter.adapter.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 서명 설정.
 *
 * <p>{@code secret} 에 기본값을 두지 않는다. 코드에 고정 비밀키가 들어가면 그 값이 배포본 전체에서
 * 같아지고, 저장소를 읽을 수 있는 누구나 관리자 토큰을 위조할 수 있다. 비어 있을 때의 처리는
 * {@link JwtSecretKeyFactory} 가 맡는다 — 죽지 않고, 대신 부팅마다 다른 임의 키를 쓰고 경고한다.
 *
 * @param secret HMAC 서명 키. HS256 이므로 최소 32바이트가 필요하다.
 * @param ttl 액세스 토큰 유효 기간
 * @param issuer 토큰 발급자 클레임
 */
@ConfigurationProperties(prefix = "nextchapter.security.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    public JwtProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofHours(8);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "nextchapter";
        }
    }
}
