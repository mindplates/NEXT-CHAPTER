package com.mindplates.nextchapter.adapter.out.persistence.generation;

import com.mindplates.nextchapter.domain.generation.model.AiBatchStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiBatchJobJpaRepository extends JpaRepository<AiBatchJobJpaEntity, Long> {

    List<AiBatchJobJpaEntity> findByStatusOrderByCreatedAtAsc(AiBatchStatus status);

    List<AiBatchJobJpaEntity> findBySkeletonIdOrderByCreatedAtAsc(Long skeletonId);

    Optional<AiBatchJobJpaEntity> findByBatchId(String batchId);
}
