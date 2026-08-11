package com.mindplates.nextchapter.adapter.out.persistence.generation;

import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationFailureJpaRepository extends JpaRepository<GenerationFailureJpaEntity, Long> {

    List<GenerationFailureJpaEntity> findByStatusOrderByCreatedAtAsc(GenerationFailureStatus status);

    List<GenerationFailureJpaEntity> findAllByOrderByCreatedAtAsc();

    List<GenerationFailureJpaEntity> findBySkeletonIdOrderByCreatedAtAsc(Long skeletonId);

    Optional<GenerationFailureJpaEntity> findByTopicAndPartitionAndOffset(String topic, int partition, long offset);
}
