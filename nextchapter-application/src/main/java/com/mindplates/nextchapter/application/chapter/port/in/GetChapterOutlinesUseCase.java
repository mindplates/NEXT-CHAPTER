package com.mindplates.nextchapter.application.chapter.port.in;

import com.mindplates.nextchapter.application.chapter.view.ChapterOutlineView;

public interface GetChapterOutlinesUseCase {

    ChapterOutlineView bySkeletonId(Long skeletonId);
}
