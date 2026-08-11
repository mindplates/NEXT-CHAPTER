package com.mindplates.nextchapter.application.skeleton.port.out;

import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.List;
import java.util.Optional;

public interface LoadSkeletonPort {

    Optional<Skeleton> findById(Long id);

    /** 주제에 붙은 뼈대. 1:1 이므로 최대 하나다. */
    Optional<Skeleton> findByTopicId(Long topicId);

    /** 뼈대가 이미 붙어 있는 주제 ID 집합. 트리 조회가 주제마다 왕복하지 않게 한다. */
    List<Long> findTopicIdsWithSkeleton();
}
