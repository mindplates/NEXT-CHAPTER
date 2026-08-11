package com.mindplates.nextchapter.adapter.out.storage.health;

import com.mindplates.nextchapter.adapter.out.storage.ObjectStorageProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 오브젝트 스토리지 연결 상태.
 *
 * <p>SDK 를 쓰지 않고 헬스 경로만 찌른다. 스토리지 수단(자체 운영 vs 클라우드)이 M6 의 미결정
 * 항목이라, M0 에서 SDK 를 고르면 코드가 그 결정을 먼저 해버린다. 연결 확인이 목적이므로
 * HTTP 한 번으로 충분하다.
 */
@Component
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final ObjectStorageProperties properties;
    private final RestClient restClient;

    public ObjectStorageHealthIndicator(ObjectStorageProperties properties) {
        this.properties = properties;
        this.restClient =
                RestClient.builder().requestFactory(requestFactory(properties)).build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(ObjectStorageProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }

    @Override
    public Health health() {
        try {
            restClient.get().uri(properties.healthUrl()).retrieve().toBodilessEntity();
            return Health.up().withDetail("endpoint", properties.endpoint()).build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", properties.endpoint())
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
