package com.mindplates.nextchapter.application.layer.port.out;

import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
import java.util.List;

/**
 * 이해도 조회 — 신호를 집계해 만든다.
 *
 * <p>집계를 <b>SQL 에서</b> 하는 이유는 경로 결정이 뼈대 전체의 챕터별 요약을 한 번에 필요로 하기 때문이다.
 * 신호 행을 다 읽어 애플리케이션에서 접으면 챕터가 수십 개인 뼈대에서 한 번의 추천이 수백 행을 읽는다.
 *
 * <p>이 포트가 개인 루프의 <b>동기 읽기</b>다. 집단 루프처럼 Kafka 를 거치면 "방금 답한 결과가 다음 챕터에
 * 반영되지 않는" 타이밍 문제가 생긴다 — 같은 트랜잭션에서 기록된 신호를 곧바로 읽는다.
 */
public interface LoadChapterUnderstandingPort {

    /** 이 뼈대의 챕터별 이해도. 신호가 없는 챕터는 <b>목록에 없다</b> — 없는 것과 0은 다르다. */
    List<ChapterUnderstanding> summarizeBySkeleton(Long userId, Long skeletonId);

    ChapterUnderstanding findByChapter(Long userId, Long chapterId);
}
