package com.mindplates.nextchapter.application.layer.port.in;

import com.mindplates.nextchapter.application.layer.view.NextChapterView;

/**
 * 경로 개인화 — <b>개인 루프의 결과가 나오는 지점</b>.
 *
 * <p>같은 뼈대·같은 진입점에서 이해도가 다른 두 사용자가 서로 다른 순서를 받는다(완료 기준 1번). 그
 * 판정은 <b>동기 읽기</b>다 — 신호를 Kafka 로만 흘리면 "방금 답한 결과가 다음 챕터에 반영되지 않는"
 * 타이밍 문제가 생긴다.
 */
public interface RecommendNextChapterUseCase {

    NextChapterView next(Long userId, Long chapterId);
}
