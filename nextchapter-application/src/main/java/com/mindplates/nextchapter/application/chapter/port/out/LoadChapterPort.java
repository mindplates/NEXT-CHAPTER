package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import java.util.List;
import java.util.Optional;

public interface LoadChapterPort {

    Optional<Chapter> findById(Long id);

    Optional<Chapter> findBySkeletonIdAndKey(Long skeletonId, String chapterKey);

    List<Chapter> findBySkeletonId(Long skeletonId);

    long countBySkeletonId(Long skeletonId);

    /** 본문이 아직 없는 챕터 수. published 전환 판정("전 챕터 완료")의 재료다. */
    long countBySkeletonIdWithoutBody(Long skeletonId);
}
