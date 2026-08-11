package com.mindplates.nextchapter.application.generation.port.in;

/**
 * ③ 본문 생성. 챕터 하나의 블록 문서를 만든다.
 *
 * @return 새로 만들었으면 true, 이미 본문이 있어 건너뛰었으면 false
 */
public interface GenerateChapterBodyUseCase {

    boolean generate(Long skeletonId, Long chapterId);
}
