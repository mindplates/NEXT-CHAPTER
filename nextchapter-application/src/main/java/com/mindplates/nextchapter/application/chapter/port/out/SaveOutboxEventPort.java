package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;

public interface SaveOutboxEventPort {

    /**
     * 이벤트를 기록한다. <b>호출자의 트랜잭션에 참여해야 한다</b> — 본문·관계와 같은 커밋에 들어가는 것이
     * outbox 패턴의 전부다. 별도 트랜잭션으로 열면 두 단계 쓰기가 되어 애초에 피하려던 불일치가 돌아온다.
     */
    OutboxEvent append(OutboxEvent event);

    void markPublished(Long eventId);
}
