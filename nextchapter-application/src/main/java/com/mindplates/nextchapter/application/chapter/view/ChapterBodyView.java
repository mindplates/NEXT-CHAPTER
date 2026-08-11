package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 챕터 본문 응답.
 *
 * <p>서버가 내리는 것은 <b>블록 문서</b>이지 HTML 이 아니다. 렌더링은 클라이언트가 하므로 새 플랫폼이
 * 생겨도 API 가 그대로다.
 *
 * @param version 이 본문의 챕터 버전. 신호 레코드가 <b>사용자가 실제로 소비한 버전</b>을 기록해야
 *     하므로 응답에 함께 실린다 — 무조건 최신 버전으로 기록하면 구버전을 본 사람의 오답이 최신
 *     버전의 실패로 잘못 집계된다
 * @param improvedFromPrevious 이전 버전에서 개선된 것인지. "이 챕터는 개선되었습니다" 배지의 근거
 */
public record ChapterBodyView(
        Long chapterId,
        String chapterKey,
        String title,
        String summary,
        int version,
        List<Block> blocks,
        boolean improvedFromPrevious,
        String changeSummary,
        LocalDateTime versionCreatedAt) {}
