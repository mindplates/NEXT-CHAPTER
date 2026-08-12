package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자에게 내리는 챕터. {@code 공용 블록 문서 + 사용자 레이어}의 합성 결과다.
 *
 * <p>서버가 내리는 것은 <b>블록 목록</b>이지 HTML 이 아니다. 새 플랫폼이 생겨도 이 API 가 그대로다.
 *
 * @param version 사용자가 <b>실제로 소비한</b> 챕터 버전. 신호 레코드가 이 값을 그대로 실어야 한다 —
 *     무조건 최신 버전으로 기록하면 구버전을 본 사람의 오답이 최신 버전의 실패로 잘못 집계된다
 * @param format 어느 형태로 받았는지. 신호에 함께 기록해 수정안의 근거로 형태별 분해를 만든다
 * @param personalized 레이어가 실제로 적용됐는지. 캐시 경계의 근거이기도 하다 — 개인화된 결과는
 *     공용 캐시에 넣을 수 없다
 * @param improvedFromPrevious "이 챕터는 개선되었습니다" 배지의 근거
 */
public record ComposedChapterView(
        Long chapterId,
        String chapterKey,
        String title,
        String summary,
        int version,
        DeliveryFormat format,
        List<Block> blocks,
        boolean personalized,
        boolean improvedFromPrevious,
        String changeSummary,
        LocalDateTime versionCreatedAt) {}
