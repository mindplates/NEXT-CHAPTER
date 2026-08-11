package com.mindplates.nextchapter.application.chapter.port.in;

import com.mindplates.nextchapter.application.chapter.view.ChapterBodyView;
import com.mindplates.nextchapter.application.chapter.view.ChapterVersionSummaryView;
import java.util.List;

public interface GetChapterUseCase {

    /** 최신 버전 본문. 사용자는 항상 최신본을 본다. */
    ChapterBodyView currentBody(Long chapterId);

    /** 특정 버전 본문. 수정 전후 비교가 임의 시점의 본문을 필요로 한다. */
    ChapterBodyView bodyAt(Long chapterId, int version);

    List<ChapterVersionSummaryView> history(Long chapterId);
}
