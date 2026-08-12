package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.GraphChapter;

/**
 * 탐색 결과의 챕터 하나. 본문은 없다 — 그래프는 얇고, 본문은 별도 조회다.
 *
 * <p>{@code chapterId} 를 함께 내리는 이유는 다음 호출(본문 조회·신호 기록)이 ID 로 이뤄지기
 * 때문이다. 키만 내리면 클라이언트가 매번 키→ID 조회를 한 번 더 해야 한다.
 */
public record ChapterNodeView(Long chapterId, String chapterKey, String title, int version) {

    public static ChapterNodeView from(GraphChapter chapter) {
        return new ChapterNodeView(chapter.chapterId(), chapter.chapterKey(), chapter.title(), chapter.version());
    }
}
