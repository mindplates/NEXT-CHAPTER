package com.mindplates.nextchapter.adapter.out.cache.chapter;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 블록 문서 캐시 설정.
 *
 * <p>TTL 이 <b>유일한 정리 수단</b>이다. 무효화 호출을 두지 않기로 했으므로, 이 값이 없으면 구버전 키가
 * Redis 에 영구히 쌓인다. 반대로 너무 짧으면 캐시가 사실상 없는 것과 같다.
 *
 * @param chapterTtl 챕터 문서 키의 수명
 */
@ConfigurationProperties(prefix = "nextchapter.cache")
public record ChapterCacheProperties(Duration chapterTtl) {

    public ChapterCacheProperties {
        if (chapterTtl == null || chapterTtl.isZero() || chapterTtl.isNegative()) {
            chapterTtl = Duration.ofHours(24);
        }
    }
}
