package com.mindplates.nextchapter.application.layer.view;

import com.mindplates.nextchapter.domain.layer.model.RecommendationReason;
import com.mindplates.nextchapter.domain.layer.model.UnderstandingLevel;
import java.util.List;

/**
 * 다음에 볼 챕터 후보.
 *
 * <p>하나만 주지 않고 <b>순서 있는 목록</b>을 준다. 그래프 탐색은 사용자가 고르는 것이고, 서버가 하나로
 * 좁히면 그건 다시 목차가 된다 — 첫 번째가 추천이고 나머지는 대안이다.
 *
 * @param currentLevel 현재 챕터에 대한 내 이해도. 왜 이런 순서가 나왔는지의 근거다
 */
public record NextChapterView(Long fromChapterId, UnderstandingLevel currentLevel, List<Candidate> candidates) {

    public record Candidate(
            Long chapterId,
            String chapterKey,
            String title,
            RecommendationReason reason,
            UnderstandingLevel level,
            boolean completed) {}
}
