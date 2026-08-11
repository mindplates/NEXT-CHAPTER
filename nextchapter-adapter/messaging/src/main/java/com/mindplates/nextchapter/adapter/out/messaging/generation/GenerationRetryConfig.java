package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * N회 자동 재시도 → 운영자 큐.
 *
 * <p>재시도 자체가 필요한 이유는 실패 원인의 대부분이 <b>벤더 쪽 일시적 상태</b>라서다. 즉시 실패로
 * 기록하면 5xx 한 번에 뼈대가 죽고, 사용자는 다시 요청해야 한다.
 *
 * <p>재시도가 <b>끝나야</b> 하는 이유는 반대편에 있다. 무한 재시도는 파티션을 영구히 막아 그 뼈대의 다른
 * 챕터까지 세우고, 실패 사실이 아무 곳에도 남지 않는다.
 */
@Configuration
@EnableConfigurationProperties(GenerationRetryProperties.class)
public class GenerationRetryConfig {

    /**
     * {@code DefaultErrorHandler} 는 컨테이너 전체에 적용된다 — 생성 리스너와 outbox 리스너가 같은 핸들러를
     * 지난다. 그래서 복구기가 생성 토픽인지 먼저 확인한다.
     *
     * <p>{@code JsonProcessingException} 을 재시도하지 않는다. 페이로드를 읽지 못하는 메시지는 몇 번 다시
     * 읽어도 같은 결과이고, 그 사이 파티션이 막힌다 — 이른바 poison message 다. 예산 초과도 재시도 대상이
     * 아니지만 그건 리스너 안에서 이미 처리되므로(즉시 실패 확정) 여기까지 오지 않는다.
     */
    @Bean
    public CommonErrorHandler generationErrorHandler(
            GenerationFailureRecoverer recoverer, GenerationRetryProperties properties) {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(properties.initialInterval().toMillis());
        backOff.setMultiplier(properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());
        // maxAttempts 는 최초 시도를 포함한다. BackOff 는 "재시도 횟수"를 세므로 하나를 뺀다.
        backOff.setMaxAttempts(Math.max(0, properties.maxAttempts() - 1));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
