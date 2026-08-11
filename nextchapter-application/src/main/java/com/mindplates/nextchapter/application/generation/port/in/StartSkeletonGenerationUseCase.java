package com.mindplates.nextchapter.application.generation.port.in;

import com.mindplates.nextchapter.application.generation.view.GenerationProgressView;

/**
 * 주제가 확정되면 뼈대 생성을 시작한다.
 *
 * <p><b>멱등이다.</b> 이미 뼈대가 있으면 새로 만들지 않고 그 진행 상태를 돌려준다 — 뼈대는 주제에
 * 1:1 이고, 같은 주제로 두 번 요청하는 것은 "합류"이지 재생성이 아니다. 재생성이 되면 신호가 붙어
 * 있는 본문이 사라지고, 그건 개선 루프의 근거를 지우는 일이다.
 */
public interface StartSkeletonGenerationUseCase {

    GenerationProgressView start(Long topicId);
}
