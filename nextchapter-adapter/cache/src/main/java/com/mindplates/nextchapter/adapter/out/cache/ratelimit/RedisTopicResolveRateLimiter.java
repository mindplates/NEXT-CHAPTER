package com.mindplates.nextchapter.adapter.out.cache.ratelimit;

import com.mindplates.nextchapter.application.catalog.port.out.TopicResolveRateLimitPort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 창 카운터.
 *
 * <p>인스턴스 메모리에 두지 않는 이유는 한도가 <b>인스턴스 수만큼 곱해지기</b> 때문이다. 그 느슨함은
 * 에러 없이 생기고, 이 엔드포인트에서는 곧 LLM 호출 비용이다.
 *
 * <p>키에 창 번호를 넣어 창이 넘어가면 <b>새 키를 보게 한다.</b> 캐시 키에 버전을 넣어 무효화 로직을
 * 없앤 것과 같은 수단이다 — 만료 시각을 따로 관리하지 않으므로 "지우는 것을 잊는" 실패가 없다.
 *
 * <p>슬라이딩 윈도우가 아니다. 창 경계에서 최대 두 배까지 통과할 수 있는 것이 대가고, 그것을 받아들이는
 * 이유는 이 리밋의 목적이 정확한 배분이 아니라 <b>폭주 차단</b>이기 때문이다.
 */
@Component
public class RedisTopicResolveRateLimiter implements TopicResolveRateLimitPort {

    private static final Logger log = LoggerFactory.getLogger(RedisTopicResolveRateLimiter.class);

    private static final String KEY_PREFIX = "ratelimit:topic-resolve:";

    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;

    public RedisTopicResolveRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.limit = properties.topicResolvePerWindow();
        this.window = properties.topicResolveWindow();
    }

    @Override
    public boolean tryConsume(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = KEY_PREFIX + userId + ":" + System.currentTimeMillis() / window.toMillis();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 첫 증가에만 TTL 을 건다. 매번 걸면 창이 호출마다 뒤로 밀려 사실상 무한 창이 된다.
                redis.expire(key, window);
            }
            return count != null && count <= limit;
        } catch (Exception e) {
            // fail-closed. 저장소가 죽었을 때 통과시키면 그 순간이 비용 폭주 창구가 된다.
            log.error("[레이트 리밋] Redis 확인 실패 — 요청을 막는다: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public int limitPerWindow() {
        return limit;
    }

    @Override
    public int windowSeconds() {
        return (int) window.toSeconds();
    }
}
