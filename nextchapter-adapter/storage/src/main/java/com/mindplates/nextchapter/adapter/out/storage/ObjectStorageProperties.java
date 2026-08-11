package com.mindplates.nextchapter.adapter.out.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오브젝트 스토리지 접속 설정.
 *
 * @param endpoint   S3 호환 엔드포인트
 * @param healthPath 헬스 확인 경로 (MinIO 기준 {@code /minio/health/live})
 * @param timeout    헬스 확인 타임아웃
 */
@ConfigurationProperties(prefix = "nextchapter.storage")
public record ObjectStorageProperties(
        String endpoint,
        String healthPath,
        Duration timeout) {

    public ObjectStorageProperties {
        healthPath = (healthPath == null || healthPath.isBlank()) ? "/minio/health/live" : healthPath;
        timeout = (timeout == null) ? Duration.ofSeconds(3) : timeout;
    }

    public String healthUrl() {
        return endpoint + healthPath;
    }
}
