package com.mindplates.nextchapter.application.chapter.port.in;

import com.mindplates.nextchapter.application.chapter.view.ChapterNeighborhoodView;
import com.mindplates.nextchapter.application.chapter.view.SkeletonEntryView;

/**
 * 사용자의 그래프 탐색. <b>published 뼈대만</b> 답한다.
 *
 * <p>검수용 조회({@link GetSkeletonGraphUseCase})와 나눈 이유가 그 조건이다. 운영자는 생성 중인 뼈대를
 * 봐야 하고 사용자는 봐서는 안 된다 — 미완성 그래프는 "관련 챕터 없음"으로 보이고, 그건 에러가 아니라
 * 잘못된 콘텐츠다.
 */
public interface ExploreChapterGraphUseCase {

    /** 주제로 진입. 사용자 경로가 주제 입력에서 시작하므로 뼈대 ID 를 몰라도 들어올 수 있어야 한다. */
    SkeletonEntryView entryPointsByTopicId(Long topicId);

    SkeletonEntryView entryPointsBySkeletonId(Long skeletonId);

    ChapterNeighborhoodView neighbors(Long chapterId, int depth);
}
