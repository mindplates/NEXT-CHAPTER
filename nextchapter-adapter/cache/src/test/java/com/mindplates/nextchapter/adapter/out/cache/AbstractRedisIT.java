package com.mindplates.nextchapter.adapter.out.cache;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 Redis 에 붙는 캐시 어댑터 테스트의 공통 기반.
 *
 * <p>목으로 덮지 않는 이유는 이 어댑터에서 틀릴 수 있는 것이 <b>Redis 명령의 의미</b>이기 때문이다 —
 * INCR 의 반환값, TTL 을 언제 거는가, 키가 실제로 만료되는가. 그건 코드를 읽어 확인할 수 있는 종류의
 * 사실이 아니다.
 *
 * <p>컨테이너를 static 으로 둬 하위 클래스가 하나를 공유한다. 클래스마다 띄우면 테스트 시간이 기동
 * 시간 × 클래스 수가 된다.
 */
@Testcontainers
public abstract class AbstractRedisIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /**
     * Spring 컨텍스트를 띄우지 않는다. 검증 대상이 빈 배선이 아니라 Redis 와의 대화이고, 컨텍스트를
     * 띄우면 그 대화를 확인하는 데 자동설정 전체가 딸려 온다.
     */
    protected static StringRedisTemplate redisTemplate() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
