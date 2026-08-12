package com.mindplates.nextchapter.application.signal.port.in;

/**
 * 미발행 신호를 집계 경로로 내보낸다.
 *
 * <p>커밋 직후에 발행하지 않고 스윕으로 두는 이유는 <b>유실을 만들지 않기 위해서</b>다. 커밋 뒤 발행이
 * 실패하면 그 신호는 집계에 영원히 도달하지 않고, 그 유실은 "임계치가 트리거되지 않는다"로 조용히
 * 나타난다. 대가는 발행이 폴링 간격만큼 늦는다는 점이고, 집단 루프는 누적 주기라 그 지연이 문제가 되지
 * 않는다 — 즉시성이 필요한 개인 루프는 같은 트랜잭션에서 이미 기록됐다.
 */
public interface PublishSignalsUseCase {

    int publishPending(int limit);
}
