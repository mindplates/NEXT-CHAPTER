package com.mindplates.nextchapter.application.layer.view;

import java.util.List;

/**
 * 뼈대 하나에 대한 내 학습 상태.
 *
 * <p>아직 보지 않은 챕터도 목록에 있다(빈 이해도로). 뼈대는 항상 완결 상태이므로 "전체 중 어디까지"를
 * 보여 줄 수 있어야 하고, 본 것만 내리면 그 분모가 사라진다.
 *
 * @param completedChapters 끝까지 소비한 챕터 수. "생성된 뼈대 중 끝까지 소비된 비율"(3개월 성공 기준
 *     3번)이 이 값에서 나온다
 */
public record SkeletonLayerView(
        Long skeletonId,
        int totalChapters,
        int visitedChapters,
        int completedChapters,
        List<ChapterUnderstandingView> chapters) {}
