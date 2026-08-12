package com.mindplates.nextchapter.adapter.out.cache.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.cache.AbstractRedisIT;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 이 리밋이 막는 것은 <b>LLM 호출 비용</b>이다. 호출 하나가 모델을 최대 3번 부르므로, 한도가 새면 그
 * 비용이 뼈대당·일일 예산 상한으로 흘러 다른 사용자의 생성까지 막는다.
 */
@DisplayName("주제 해소 레이트 리밋 (실제 Redis)")
class RedisTopicResolveRateLimiterIT extends AbstractRedisIT {

    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis = redisTemplate();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private RedisTopicResolveRateLimiter limiter(int limit, Duration window) {
        return new RedisTopicResolveRateLimiter(redis, new RateLimitProperties(limit, window));
    }

    @Test
    @DisplayName("한도까지 통과하고 그 다음을 막는다")
    void allowsUpToLimit() {
        RedisTopicResolveRateLimiter limiter = limiter(3, Duration.ofMinutes(10));

        assertThat(limiter.tryConsume(42L)).isTrue();
        assertThat(limiter.tryConsume(42L)).isTrue();
        assertThat(limiter.tryConsume(42L)).isTrue();
        assertThat(limiter.tryConsume(42L)).isFalse();
    }

    /** 사용자별로 세지 않으면 한 사람이 전체 서비스의 주제 입력을 막을 수 있다. */
    @Test
    @DisplayName("한도는 사용자별이다")
    void countsPerUser() {
        RedisTopicResolveRateLimiter limiter = limiter(1, Duration.ofMinutes(10));

        assertThat(limiter.tryConsume(42L)).isTrue();
        assertThat(limiter.tryConsume(42L)).isFalse();
        assertThat(limiter.tryConsume(43L)).isTrue();
    }

    /**
     * 창이 넘어가면 새 키를 보게 되므로 카운터가 저절로 처음부터 시작한다. 지우는 작업이 없다는 것이
     * 요점이다 — 무효화 호출은 한 번만 누락돼도 사용자가 영구히 막힌다.
     */
    @Test
    @DisplayName("창이 넘어가면 다시 통과한다")
    void windowRollsOver() throws InterruptedException {
        RedisTopicResolveRateLimiter limiter = limiter(1, Duration.ofMillis(700));

        assertThat(limiter.tryConsume(42L)).isTrue();
        assertThat(limiter.tryConsume(42L)).isFalse();

        Thread.sleep(800);

        assertThat(limiter.tryConsume(42L)).isTrue();
    }

    /** TTL 이 없으면 키가 영구히 남아 그 사용자가 창 하나에 갇힌다. */
    @Test
    @DisplayName("카운터 키에 TTL 이 걸린다")
    void setsExpiry() {
        RedisTopicResolveRateLimiter limiter = limiter(5, Duration.ofMinutes(10));
        limiter.tryConsume(42L);

        String key = redis.keys("ratelimit:topic-resolve:42:*").iterator().next();

        assertThat(redis.getExpire(key)).isPositive();
    }

    /** 사용자를 특정할 수 없는 호출은 통과시키지 않는다 — 그 경로가 열리면 리밋이 없는 것과 같다. */
    @Test
    @DisplayName("사용자 없는 호출은 막는다")
    void rejectsNullUser() {
        assertThat(limiter(5, Duration.ofMinutes(10)).tryConsume(null)).isFalse();
    }

    @Test
    @DisplayName("설정값이 비면 기본값을 쓴다")
    void fillsDefaults() {
        RateLimitProperties defaults = new RateLimitProperties(0, null);

        assertThat(defaults.topicResolvePerWindow()).isEqualTo(10);
        assertThat(defaults.topicResolveWindow()).isEqualTo(Duration.ofMinutes(10));
        assertThat(limiter(defaults.topicResolvePerWindow(), defaults.topicResolveWindow())
                        .limitPerWindow())
                .isEqualTo(10);
        assertThat(limiter(defaults.topicResolvePerWindow(), defaults.topicResolveWindow())
                        .windowSeconds())
                .isEqualTo(600);
    }
}
