package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.Chapter;

public interface SaveChapterPort {

    Chapter save(Chapter chapter);
}
