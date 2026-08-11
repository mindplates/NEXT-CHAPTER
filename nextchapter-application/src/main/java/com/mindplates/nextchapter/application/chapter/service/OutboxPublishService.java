package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.PublishOutboxUseCase;
import com.mindplates.nextchapter.application.chapter.port.out.LoadOutboxEventPort;
import com.mindplates.nextchapter.application.chapter.port.out.OutboxPublisherPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveOutboxEventPort;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 퍼블리셔.
 *
 * <p>발행과 발행 표시를 <b>같은 트랜잭션</b>에 둔다. 표시가 커밋되지 않으면 다음 폴링이 같은 이벤트를 다시
 * 집어 가는데, 그게 옳은 방향이다 — Neo4j 반영이 멱등이므로 <b>중복 발행은 안전하고 누락은 아니다.</b>
 * 반대로 표시를 먼저 커밋하고 발행하면 발행 실패가 곧 유실이 된다.
 *
 * <p>실패한 이벤트 하나가 뒤의 전부를 막지 않게 건별로 격리한다. 다만 표시하지 않은 채 넘어가므로 다음
 * 폴링이 다시 시도한다 — 순서가 중요한 파티션 키 안에서는 앞이 막히면 뒤도 밀리지만, 그건 순서를 지키기
 * 위한 대가다.
 */
@Service
@RequiredArgsConstructor
public class OutboxPublishService implements PublishOutboxUseCase {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishService.class);

    private final LoadOutboxEventPort loadOutboxEventPort;
    private final SaveOutboxEventPort saveOutboxEventPort;
    private final OutboxPublisherPort outboxPublisherPort;

    @Override
    @Transactional
    public int publishPending(int limit) {
        List<OutboxEvent> claimed = loadOutboxEventPort.claimUnpublished(limit);
        int published = 0;
        for (OutboxEvent event : claimed) {
            try {
                outboxPublisherPort.publish(event);
                saveOutboxEventPort.markPublished(event.id());
                published++;
            } catch (RuntimeException e) {
                log.error(
                        "[outbox] 발행 실패, 다음 폴링에서 다시 시도한다 id={} type={}: {}",
                        event.id(),
                        event.eventType(),
                        e.getMessage());
            }
        }
        if (published > 0) {
            log.debug("[outbox] {}건 발행", published);
        }
        return published;
    }
}
