package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;
import java.util.List;

public interface LoadOutboxEventPort {

    /**
     * 미발행 이벤트를 ID 순으로 집어 온다.
     *
     * <p>구현은 행을 잠그고 다른 퍼블리셔가 건너뛰게 해야 한다({@code FOR UPDATE SKIP LOCKED}). 인스턴스가
     * 두 개 이상일 때 잠그지 않으면 같은 이벤트를 둘이 발행하고, 잠그되 건너뛰지 않으면 한쪽이 다른 쪽을
     * 기다리며 처리량이 1로 떨어진다.
     */
    List<OutboxEvent> claimUnpublished(int limit);

    /** 이 뼈대의 미발행 이벤트 수. published 전환 조건이 0 을 확인한다. */
    long countPendingBySkeletonId(Long skeletonId);
}
