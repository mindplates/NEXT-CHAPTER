package com.mindplates.nextchapter.application.layer.port.in;

import com.mindplates.nextchapter.application.layer.view.ChapterUnderstandingView;
import com.mindplates.nextchapter.application.layer.view.SkeletonLayerView;

/**
 * 내 학습 상태 조회 — 개인 루프의 읽기 쪽.
 *
 * <p>사용자 자신의 레이어만 볼 수 있다. 사용자 ID 를 파라미터로 받는 것은 호출부(웹 어댑터)가 토큰에서
 * 꺼내 넘기기 때문이고, 다른 사용자의 ID 를 넣을 수 있는 <b>경로가 없다</b> — 그 값은 요청 본문이 아니라
 * 토큰에서만 온다.
 */
public interface GetChapterLayerUseCase {

    SkeletonLayerView bySkeletonId(Long userId, Long skeletonId);

    ChapterUnderstandingView byChapterId(Long userId, Long chapterId);
}
