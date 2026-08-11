package com.mindplates.nextchapter.application.generation.port.in;

/**
 * 공개 조건을 만족한 뼈대를 {@code PUBLISHED} 로 넘긴다.
 *
 * <p>스윕으로 도는 이유는 조건에 <b>outbox 가 비었는가</b>가 들어가기 때문이다. outbox 퍼블리셔는
 * 폴링이므로 마지막 챕터가 커밋된 순간에는 아직 발행이 남아 있다 — 그 시점에 판정하면 항상 실패하고,
 * 실패한 채로 넘어가면 아무도 다시 판정하지 않는다.
 *
 * <p>커밋 후 훅으로 즉시 판정하지 않는 이유도 같다. 프로세스가 그 사이 죽으면 뼈대가 영원히
 * {@code GENERATING_ASSETS} 에 남는다. 스윕은 그 경우에도 다음 주기에 집어 간다.
 *
 * @return 이번에 공개된 뼈대 수
 */
public interface PublishReadySkeletonsUseCase {

    int publishReady();
}
