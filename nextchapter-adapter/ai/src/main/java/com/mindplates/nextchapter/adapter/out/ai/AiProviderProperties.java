package com.mindplates.nextchapter.adapter.out.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 벤더 엔드포인트와 타임아웃.
 *
 * <p>모델은 여기 없다 — 모델은 관리 화면 설정에서 온다. 여기 있는 것은 "어디로 붙는가"뿐이고,
 * 그건 벤더가 정하는 값이라 관리자가 고칠 대상이 아니다 (프록시·게이트웨이를 끼울 때만 바뀐다).
 *
 * <p>{@code readTimeout} 이 긴 이유는 본문 생성이 수십 초 걸리기 때문이다. 짧게 잡으면 생성이
 * 거의 끝난 호출을 버리고 재시도해 비용만 두 배가 된다.
 */
@ConfigurationProperties(prefix = "nextchapter.ai")
public record AiProviderProperties(
        String anthropicBaseUrl,
        String anthropicVersion,
        String voyageBaseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public AiProviderProperties {
        anthropicBaseUrl = orDefault(anthropicBaseUrl, "https://api.anthropic.com");
        anthropicVersion = orDefault(anthropicVersion, "2023-06-01");
        voyageBaseUrl = orDefault(voyageBaseUrl, "https://api.voyageai.com");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(5) : readTimeout;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
