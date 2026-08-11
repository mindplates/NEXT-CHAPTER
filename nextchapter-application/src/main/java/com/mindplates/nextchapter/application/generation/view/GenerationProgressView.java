package com.mindplates.nextchapter.application.generation.view;

import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;

/**
 * 생성 진행률. 완료 알림과 관리 화면이 같은 값을 본다.
 *
 * <p>{@code percent} 는 <b>어림값이다.</b> 단계별 가중치를 고정해 계산하지만 실제 소요시간은 ④ 파생
 * 자산 생성이 지배한다(영상은 TTS·렌더가 붙어 다른 단계와 자릿수가 다르다). 그래서 챕터 수와 본문
 * 완료 수를 함께 내린다 — 퍼센트만 보여주면 "80%에서 한 시간"이 버그처럼 보인다.
 */
public record GenerationProgressView(
        Long skeletonId,
        Long topicId,
        SkeletonStatus status,
        int totalChapters,
        int chaptersWithBody,
        int percent,
        boolean published,
        boolean failed) {

    /** 단계별 가중치의 상한. 본문 생성 구간을 챕터 완료 비율로 보간한다. */
    private static final int GRAPH_DONE = 10;

    private static final int OUTLINES_DONE = 25;
    private static final int BODIES_DONE = 75;
    private static final int ASSETS_IN_PROGRESS = 90;

    public static GenerationProgressView of(
            Long skeletonId, Long topicId, SkeletonStatus status, int totalChapters, int chaptersWithBody) {
        return new GenerationProgressView(
                skeletonId,
                topicId,
                status,
                totalChapters,
                chaptersWithBody,
                percentOf(status, totalChapters, chaptersWithBody),
                status == SkeletonStatus.PUBLISHED,
                status == SkeletonStatus.FAILED);
    }

    private static int percentOf(SkeletonStatus status, int totalChapters, int chaptersWithBody) {
        return switch (status) {
            case GENERATING_GRAPH -> 0;
            case GENERATING_OUTLINES -> GRAPH_DONE;
            case GENERATING_BODIES -> interpolateBodies(totalChapters, chaptersWithBody);
            case GENERATING_ASSETS -> ASSETS_IN_PROGRESS;
            case PUBLISHED -> 100;
                // 실패는 진행률로 표현하지 않는다. 숫자를 남기면 "멈춘 것"과 "느린 것"이 구분되지 않는다.
            case FAILED -> 0;
        };
    }

    private static int interpolateBodies(int totalChapters, int chaptersWithBody) {
        if (totalChapters <= 0) {
            return OUTLINES_DONE;
        }
        int span = BODIES_DONE - OUTLINES_DONE;
        return OUTLINES_DONE + (int) Math.round((double) span * chaptersWithBody / totalChapters);
    }
}
