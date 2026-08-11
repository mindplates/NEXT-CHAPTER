package com.mindplates.nextchapter.application.generation.port.in;

/**
 * ① 그래프 생성. 챕터 제목·요약과 챕터 간 관계를 만든다 — 본문은 만들지 않는다.
 *
 * @return 만들어진 챕터 수. 0 이면 다음 단계로 넘어갈 수 없다
 */
public interface GenerateSkeletonGraphUseCase {

    int generate(Long skeletonId);
}
