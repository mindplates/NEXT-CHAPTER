package com.mindplates.nextchapter.application.generation.port.in;

/**
 * ② 개요 생성. 챕터별 개요를 쓰고 챕터 간 중복·누락을 보고한다.
 *
 * @return 개요를 쓴 챕터 수
 */
public interface GenerateChapterOutlinesUseCase {

    int generate(Long skeletonId);
}
