package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;

public interface SaveChapterVersionPort {

    ChapterVersion save(ChapterVersion version);
}
