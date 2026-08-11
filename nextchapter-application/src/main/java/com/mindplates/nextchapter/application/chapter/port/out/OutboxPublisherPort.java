package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;

/** outbox 이벤트를 Kafka 로 내보낸다. 파티션 키는 이벤트가 들고 있다. */
public interface OutboxPublisherPort {

    void publish(OutboxEvent event);
}
