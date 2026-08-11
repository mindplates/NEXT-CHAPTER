package com.mindplates.nextchapter.adapter.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 서명 키를 만든다. 설정값이 있으면 그것을, 없으면 부팅마다 다른 임의 키를 쓴다.
 *
 * <p>설정이 없을 때 기동을 막지 않는 이유는 개발 편의고, 고정 기본키를 코드에 두지 않는 이유는
 * 보안이다. 둘을 동시에 만족시키는 지점이 "임의 키 + 경고"다 — 재기동하면 발급된 토큰이 전부
 * 무효가 되므로 운영에서는 반드시 설정하게 된다. 반대로 고정 기본키를 두면 아무 증상 없이
 * 배포본 전체가 같은 키를 쓰는 상태가 되고, 그건 조용히 틀린다.
 */
final class JwtSecretKeyFactory {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretKeyFactory.class);

    /** HS256 의 최소 키 길이. Nimbus 가 이보다 짧은 키를 거절한다. */
    private static final int MIN_KEY_BYTES = 32;

    private static final String ALGORITHM = "HmacSHA256";

    private JwtSecretKeyFactory() {}

    static SecretKey create(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("[보안] nextchapter.security.jwt.secret 이 없어 임의 키로 서명한다. "
                    + "재기동하면 발급된 모든 관리자 토큰이 무효가 된다 — 운영 환경에서는 반드시 설정할 것.");
            byte[] random = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(random);
            return new SecretKeySpec(random, ALGORITHM);
        }

        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("nextchapter.security.jwt.secret 은 최소 " + MIN_KEY_BYTES + "바이트여야 합니다.");
        }
        return new SecretKeySpec(bytes, ALGORITHM);
    }
}
