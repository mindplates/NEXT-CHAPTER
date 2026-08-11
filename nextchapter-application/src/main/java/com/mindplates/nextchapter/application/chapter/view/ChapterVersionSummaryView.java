package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import java.time.LocalDateTime;

/** 이력 한 줄. 본문 없이 "언제 무엇이 왜 바뀌었는가"만 본다. */
public record ChapterVersionSummaryView(
        int version,
        ChapterVersionSource source,
        String changeSummary,
        String createdBy,
        int blockCount,
        LocalDateTime createdAt) {}
