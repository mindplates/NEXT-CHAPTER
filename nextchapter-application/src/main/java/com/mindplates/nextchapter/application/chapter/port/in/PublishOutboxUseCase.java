package com.mindplates.nextchapter.application.chapter.port.in;

/**
 * 미발행 outbox 이벤트를 내보낸다.
 *
 * @return 발행한 건수. 0 이면 밀린 것이 없다
 */
public interface PublishOutboxUseCase {

    int publishPending(int limit);
}
