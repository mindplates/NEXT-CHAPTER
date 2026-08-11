package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import java.util.List;

public interface LoadChapterRelationPort {

    List<ChapterRelation> findBySkeletonId(Long skeletonId);

    long countBySkeletonId(Long skeletonId);
}
