package com.mindplates.nextchapter.adapter.out.persistence.skeleton;

import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkeletonJpaRepository extends JpaRepository<SkeletonJpaEntity, Long> {

    Optional<SkeletonJpaEntity> findByTopicId(Long topicId);

    @Query("SELECT s.topicId FROM SkeletonJpaEntity s")
    List<Long> findAllTopicIds();

    List<SkeletonJpaEntity> findByStatusOrderByIdAsc(SkeletonStatus status);
}
