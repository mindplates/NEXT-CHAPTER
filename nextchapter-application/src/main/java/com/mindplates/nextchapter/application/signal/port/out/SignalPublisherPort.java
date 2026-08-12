package com.mindplates.nextchapter.application.signal.port.out;

import com.mindplates.nextchapter.domain.signal.model.Signal;

/**
 * 집단 루프로 신호를 내보낸다.
 *
 * <p>발행 실패를 <b>삼키지 않는다.</b> 삼키면 발행 표시가 커밋되어 그 신호가 집계에 영원히 도달하지
 * 않는다 — outbox 퍼블리셔와 같은 규칙이다.
 */
public interface SignalPublisherPort {

    void publish(Signal signal);
}
