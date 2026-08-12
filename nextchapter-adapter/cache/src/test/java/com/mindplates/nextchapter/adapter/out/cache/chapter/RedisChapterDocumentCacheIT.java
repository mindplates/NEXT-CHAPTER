package com.mindplates.nextchapter.adapter.out.cache.chapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.cache.AbstractRedisIT;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 이 캐시에서 조용히 틀릴 수 있는 것은 <b>키</b>와 <b>직렬화</b>다. 키에 버전이 빠지면 사용자가 수정 전
 * 본문을 계속 보고, 블록의 속성이 왕복에서 사라지면 도표가 빈 칸으로 렌더된다 — 둘 다 에러가 나지 않는다.
 */
@DisplayName("블록 문서 캐시 (실제 Redis)")
class RedisChapterDocumentCacheIT extends AbstractRedisIT {

    private StringRedisTemplate redis;
    private RedisChapterDocumentCache cache;

    @BeforeEach
    void setUp() {
        redis = redisTemplate();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        cache = new RedisChapterDocumentCache(redis, new ChapterCacheProperties(Duration.ofHours(1)));
    }

    private static CachedChapterDocument document(int version, DeliveryFormat format) {
        return new CachedChapterDocument(
                100L,
                version,
                format,
                List.of(
                        Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                        Block.figure("b2", Map.of("type", "plot", "series", List.of(1, 2, 3)))),
                true,
                "b2 설명 보강",
                LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    /** 속성이 왕복에서 사라지면 도표가 빈 칸으로 렌더된다 — 에러 없이 콘텐츠만 빠진다. */
    @Test
    @DisplayName("블록 타입·속성·버전 메타가 왕복에서 살아남는다")
    void roundTripsDocument() {
        cache.put(document(2, DeliveryFormat.WEB));

        CachedChapterDocument found = cache.find(100L, 2, DeliveryFormat.WEB).orElseThrow();

        assertThat(found.version()).isEqualTo(2);
        assertThat(found.format()).isEqualTo(DeliveryFormat.WEB);
        assertThat(found.improvedFromPrevious()).isTrue();
        assertThat(found.changeSummary()).isEqualTo("b2 설명 보강");
        assertThat(found.versionCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 9, 0));
        assertThat(found.blocks()).extracting(Block::id).containsExactly("b1", "b2");
        assertThat(found.blocks().get(0).type()).isEqualTo(BlockType.HEADING);
        assertThat(found.blocks().get(1).attributes()).containsKey("spec");
    }

    /**
     * <b>이 테스트가 캐시 전략의 전부다.</b> 버전이 오르면 새 키를 보게 되므로 무효화 호출이 필요 없다.
     * 키에 버전이 없으면 수정 후에도 구버전이 계속 나가고, 그건 "개선되었습니다" 배지와 본문이 어긋나는
     * 상태로 조용히 나타난다.
     */
    @Test
    @DisplayName("버전이 오르면 다른 키다 — 무효화 없이 새 본문이 나온다")
    void versionIsPartOfKey() {
        cache.put(document(2, DeliveryFormat.WEB));

        assertThat(cache.find(100L, 3, DeliveryFormat.WEB)).isEmpty();
        assertThat(cache.find(100L, 2, DeliveryFormat.WEB)).isPresent();
    }

    /** 형태가 키에 없으면 웹 사용자가 narration 이 섞인 목록을 받는다. */
    @Test
    @DisplayName("형태도 키의 일부다")
    void formatIsPartOfKey() {
        cache.put(document(2, DeliveryFormat.WEB));

        assertThat(cache.find(100L, 2, DeliveryFormat.VIDEO)).isEmpty();
    }

    @Test
    @DisplayName("키 모양은 chapter:{id}:v{version}:{format} 이다")
    void keyShape() {
        cache.put(document(2, DeliveryFormat.WEB));

        assertThat(redis.hasKey("chapter:100:v2:web")).isTrue();
    }

    /** TTL 이 유일한 정리 수단이다 — 없으면 구버전 키가 영구히 쌓인다. */
    @Test
    @DisplayName("키에 TTL 이 걸린다")
    void setsTtl() {
        cache.put(document(2, DeliveryFormat.WEB));

        assertThat(redis.getExpire("chapter:100:v2:web")).isPositive();
    }

    @Test
    @DisplayName("없는 키는 미스다")
    void missWhenAbsent() {
        assertThat(cache.find(999L, 1, DeliveryFormat.WEB)).isEmpty();
    }

    /**
     * 배포로 블록 모양이 바뀌면 옛 값이 읽히지 않는다. 그때 예외를 올리면 배포 직후 모든 챕터 조회가
     * 실패하므로, 미스로 접어 원천에서 다시 읽게 한다.
     */
    @Test
    @DisplayName("깨진 값은 미스로 접는다 — 조회를 실패시키지 않는다")
    void corruptValueIsMiss() {
        redis.opsForValue().set("chapter:100:v2:web", "{not-json");

        assertThat(cache.find(100L, 2, DeliveryFormat.WEB)).isEmpty();
    }

    @Test
    @DisplayName("TTL 설정이 비면 기본값을 쓴다")
    void fillsDefaultTtl() {
        assertThat(new ChapterCacheProperties(null).chapterTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(new ChapterCacheProperties(Duration.ZERO).chapterTtl()).isEqualTo(Duration.ofHours(24));
    }
}
