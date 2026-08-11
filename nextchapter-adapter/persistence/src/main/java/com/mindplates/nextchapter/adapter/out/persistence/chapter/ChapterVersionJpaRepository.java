package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterVersionJpaRepository extends JpaRepository<ChapterVersionJpaEntity, Long> {

    Optional<ChapterVersionJpaEntity> findByChapterIdAndVersion(Long chapterId, int version);

    Optional<ChapterVersionJpaEntity> findFirstByChapterIdOrderByVersionDesc(Long chapterId);

    List<ChapterVersionJpaEntity> findByChapterIdOrderByVersionDesc(Long chapterId);
}
