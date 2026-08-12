package com.mindplates.nextchapter.adapter.out.cache.chapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 블록 문서 캐시. 키는 {@code chapter:{id}:v{version}:{format}} 다.
 *
 * <p><b>키에 버전이 있으므로 무효화 코드가 없다.</b> 뼈대가 수정되면 버전이 올라 새 키를 보게 되고,
 * 구버전 키는 TTL 로 사라진다. 챕터 버전을 전체 스냅샷으로 저장하기로 했으므로 버전 번호는 공짜로 생긴다.
 *
 * <p><b>캐시 실패는 조회를 막지 않는다.</b> 캐시는 파생이고 원천은 Postgres 다 — Redis 가 죽었을 때
 * 학습이 멈추는 것은 받아들일 수 없다. 레이트 리밋이 반대로 fail-closed 인 것과 대조되는데, 그쪽은
 * 실패가 <b>비용</b>으로 나타나고 이쪽은 <b>느려지는</b> 것으로 나타나기 때문이다.
 *
 * <p>역직렬화 실패도 미스로 접는다. 배포로 {@code Block} 모양이 바뀌면 옛 값이 읽히지 않는데, 그때
 * 예외를 올리면 배포 직후 모든 챕터 조회가 실패한다 — 원천에서 다시 읽고 새 값으로 덮는 편이 맞다.
 */
@Component
public class RedisChapterDocumentCache implements ChapterDocumentCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisChapterDocumentCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChapterDocumentCache(StringRedisTemplate redis, ChapterCacheProperties properties) {
        this.redis = redis;
        // 자체 ObjectMapper 를 쓴다. 웹 어댑터의 매퍼 설정이 바뀌면 캐시 형식이 함께 바뀌는데,
        // 그건 배포 시점에 전량 미스로 나타나고 원인을 찾기 어렵다.
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.ttl = properties.chapterTtl();
    }

    static String key(Long chapterId, int version, DeliveryFormat format) {
        return "chapter:" + chapterId + ":v" + version + ":" + format.name().toLowerCase();
    }

    @Override
    public Optional<CachedChapterDocument> find(Long chapterId, int version, DeliveryFormat format) {
        try {
            String raw = redis.opsForValue().get(key(chapterId, version, format));
            return raw == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(raw, CachedChapterDocument.class));
        } catch (Exception e) {
            log.warn(
                    "[캐시] 챕터 문서 읽기 실패 — 원천에서 읽는다 chapterId={} {}",
                    chapterId,
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(CachedChapterDocument document) {
        try {
            redis.opsForValue()
                    .set(
                            key(document.chapterId(), document.version(), document.format()),
                            objectMapper.writeValueAsString(document),
                            ttl);
        } catch (Exception e) {
            // 저장 실패는 다음 조회가 원천을 읽는 것으로 끝난다. 조회를 실패시킬 이유가 없다.
            log.warn(
                    "[캐시] 챕터 문서 저장 실패 chapterId={} {}",
                    document.chapterId(),
                    e.getClass().getSimpleName());
        }
    }
}
