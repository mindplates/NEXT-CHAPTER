package com.mindplates.nextchapter.config;

import com.mindplates.nextchapter.application.chapter.port.in.PublishOutboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox 퍼블리셔를 주기적으로 돈다.
 *
 * <p>폴링을 쓰는 이유는 <b>커밋 시점을 알 수 없기 때문</b>이다. 트랜잭션 커밋 후 훅으로 즉시 발행하면
 * 지연은 줄지만, 프로세스가 커밋 직후 죽으면 그 이벤트를 다시 집어 갈 사람이 없다. 폴링은 그 경우에도
 * 다음 주기에 반드시 집어 간다 — 지연을 대가로 유실 없음을 얻는다.
 *
 * <p>{@code fixedDelay} 를 쓰는 이유는 한 주기가 길어졌을 때 다음 주기가 겹쳐 돌지 않게 하기 위해서다.
 * {@code fixedRate} 는 밀린 만큼 몰아서 실행해 Neo4j 쓰기가 한꺼번에 몰린다.
 *
 * <p>조립 모듈에 두는 이유는 "언제 부르는가"가 기동 절차의 관심사이기 때문이다. 유스케이스는 "밀린 것을
 * 내보낸다"만 안다.
 */
@Component
public class OutboxPublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishScheduler.class);

    private final PublishOutboxUseCase publishOutboxUseCase;
    private final int pollLimit;

    public OutboxPublishScheduler(
            PublishOutboxUseCase publishOutboxUseCase,
            @org.springframework.beans.factory.annotation.Value("${nextchapter.kafka.outbox.poll-limit:100}")
                    int pollLimit) {
        this.publishOutboxUseCase = publishOutboxUseCase;
        this.pollLimit = pollLimit;
    }

    @Scheduled(fixedDelayString = "${nextchapter.kafka.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        try {
            publishOutboxUseCase.publishPending(pollLimit);
        } catch (RuntimeException e) {
            // 폴링 스레드가 죽으면 이후 이벤트가 영원히 발행되지 않는다. 예외를 밖으로 내보내지 않는다.
            log.error("[outbox] 폴링 주기 실패, 다음 주기에 다시 시도한다: {}", e.getMessage());
        }
    }
}
