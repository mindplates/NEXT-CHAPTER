package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterJpaRepository extends JpaRepository<ChapterJpaEntity, Long> {

    List<ChapterJpaEntity> findBySkeletonIdOrderBySortOrderAscIdAsc(Long skeletonId);

    Optional<ChapterJpaEntity> findBySkeletonIdAndChapterKey(Long skeletonId, String chapterKey);

    long countBySkeletonId(Long skeletonId);

    /** 본문이 없는 챕터 수. published 전환 조건("전 챕터 완료")을 한 쿼리로 판정한다. */
    long countBySkeletonIdAndCurrentVersion(Long skeletonId, int currentVersion);
}
