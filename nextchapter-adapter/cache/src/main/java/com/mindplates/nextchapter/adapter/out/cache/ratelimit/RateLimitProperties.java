package com.mindplates.nextchapter.adapter.out.cache.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 레이트 리밋 설정값.
 *
 * <p>코드 상수가 아닌 이유는 초기 사용 패턴을 모르기 때문이다. 실제로 얼마가 맞는지는 돌려 봐야 알고,
 * 그때 재배포 없이 조절할 수 있어야 한다 — 임계치·예산 상한을 설정값으로 뺀 것과 같은 이유다.
 *
 * @param topicResolvePerWindow 창 하나당 허용 횟수
 * @param topicResolveWindow 창 길이
 */
@ConfigurationProperties(prefix = "nextchapter.rate-limit")
public record RateLimitProperties(int topicResolvePerWindow, Duration topicResolveWindow) {

    public RateLimitProperties {
        if (topicResolvePerWindow <= 0) {
            topicResolvePerWindow = 10;
        }
        if (topicResolveWindow == null || topicResolveWindow.isZero() || topicResolveWindow.isNegative()) {
            topicResolveWindow = Duration.ofMinutes(10);
        }
    }
}
