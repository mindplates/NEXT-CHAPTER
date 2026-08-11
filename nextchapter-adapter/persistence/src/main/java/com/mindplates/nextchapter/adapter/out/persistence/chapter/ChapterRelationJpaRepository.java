package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRelationJpaRepository extends JpaRepository<ChapterRelationJpaEntity, Long> {

    List<ChapterRelationJpaEntity> findBySkeletonIdOrderByIdAsc(Long skeletonId);

    long countBySkeletonId(Long skeletonId);

    void deleteBySkeletonId(Long skeletonId);
}
