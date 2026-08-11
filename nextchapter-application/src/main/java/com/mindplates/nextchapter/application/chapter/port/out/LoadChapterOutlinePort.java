package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import java.util.List;
import java.util.Optional;

public interface LoadChapterOutlinePort {

    Optional<ChapterOutline> findByChapterId(Long chapterId);

    List<ChapterOutline> findBySkeletonId(Long skeletonId);
}
