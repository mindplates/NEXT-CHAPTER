package com.mindplates.nextchapter.adapter.out.messaging.generation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 생성 단계 재시도 정책.
 *
 * <p>지수 백오프를 쓰는 이유는 실패 원인의 대부분이 <b>벤더 쪽 일시적 상태</b>(5xx, 레이트리밋,
 * 타임아웃)이기 때문이다. 고정 간격으로 즉시 다시 때리면 레이트리밋을 더 오래 유지시킨다.
 *
 * <p>{@code maxAttempts} 를 작게 두는 이유는 재시도가 <b>컨슈머 스레드를 붙잡기</b> 때문이다. 한 메시지가
 * (생성 시간 + 백오프) × 시도 횟수만큼 파티션을 잡고 있고, 그 총합이 {@code max.poll.interval.ms} 를 넘으면
 * 브로커가 컨슈머를 죽은 것으로 보고 리밸런스를 일으킨다 — 그러면 그 뼈대의 다른 챕터까지 전부 밀린다.
 *
 * <p>본문 생성이 수십 초이므로 기본값(3회, 5초→10초)의 최대 점유는 대략 2분이다. 상한을 올리려면
 * {@code max.poll.interval.ms} 를 함께 올려야 한다.
 *
 * @param maxAttempts 최초 시도를 포함한 총 시도 횟수
 */
@ConfigurationProperties(prefix = "nextchapter.kafka.retry")
public record GenerationRetryProperties(
        int maxAttempts, Duration initialInterval, double multiplier, Duration maxInterval) {

    public GenerationRetryProperties {
        maxAttempts = maxAttempts < 1 ? 3 : maxAttempts;
        initialInterval = initialInterval == null ? Duration.ofSeconds(5) : initialInterval;
        multiplier = multiplier < 1.0 ? 2.0 : multiplier;
        maxInterval = maxInterval == null ? Duration.ofSeconds(60) : maxInterval;
    }
}
