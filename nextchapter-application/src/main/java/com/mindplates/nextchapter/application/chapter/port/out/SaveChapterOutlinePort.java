package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import java.util.List;

public interface SaveChapterOutlinePort {

    /** 챕터당 하나이므로 저장은 곧 덮어쓰기다. */
    List<ChapterOutline> saveAll(List<ChapterOutline> outlines);
}
