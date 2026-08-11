package com.mindplates.nextchapter.application.skeleton.port.out;

import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import java.util.Optional;

public interface LoadSkeletonPort {

    Optional<Skeleton> findById(Long id);

    /** 주제에 붙은 뼈대. 1:1 이므로 최대 하나다. */
    Optional<Skeleton> findByTopicId(Long topicId);

    /** 뼈대가 이미 붙어 있는 주제 ID 집합. 트리 조회가 주제마다 왕복하지 않게 한다. */
    List<Long> findTopicIdsWithSkeleton();

    /**
     * 이 상태의 뼈대들. published 전환 스윕이 {@code GENERATING_ASSETS} 를 훑는다.
     *
     * <p>스윕이 필요한 이유는 전환 조건에 <b>outbox 가 비었는가</b>가 들어가기 때문이다. outbox 퍼블리셔는
     * 폴링이라 마지막 챕터가 커밋된 순간에는 아직 발행이 남아 있고, 그 시점에 판정하면 항상 실패한다.
     */
    List<Skeleton> findByStatus(SkeletonStatus status);
}
