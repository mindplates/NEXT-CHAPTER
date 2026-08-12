package com.mindplates.nextchapter.application.layer.view;

import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
import com.mindplates.nextchapter.domain.layer.model.UnderstandingLevel;
import java.time.LocalDateTime;

/**
 * 한 챕터에 대한 내 상태.
 *
 * <p>구간({@code level})과 원자료(시도·정답·질문)를 함께 내린다. 구간만 주면 화면이 "왜 그렇게 판정됐나"를
 * 보여 줄 수 없고, 원자료만 주면 클라이언트가 구간 규칙을 복제하게 된다 — 그 규칙이 서버와 어긋나는 순간
 * 화면과 추천이 다른 말을 한다.
 */
public record ChapterUnderstandingView(
        Long chapterId,
        String chapterKey,
        String title,
        int attempts,
        int correct,
        int questions,
        int progressPercent,
        UnderstandingLevel level,
        boolean completed,
        LocalDateTime lastSeenAt) {

    public static ChapterUnderstandingView of(Chapter chapter, ChapterUnderstanding understanding) {
        return new ChapterUnderstandingView(
                chapter.id(),
                chapter.chapterKey(),
                chapter.title(),
                understanding.attempts(),
                understanding.correct(),
                understanding.questions(),
                understanding.progressPercent(),
                understanding.level(),
                understanding.completed(),
                understanding.lastSeenAt());
    }
}
