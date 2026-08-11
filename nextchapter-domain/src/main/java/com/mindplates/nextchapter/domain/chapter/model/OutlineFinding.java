package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 개요 검사 소견 하나.
 *
 * @param chapterKeys 관련 챕터 키. 조인 대상이 아니라 사람이 읽는 근거다 — 검수 화면이 "어느 챕터끼리
 *     겹치는가"를 보여줄 수 있어야 무엇을 고칠지 결정할 수 있다
 */
public record OutlineFinding(
        Long id,
        Long skeletonId,
        OutlineFindingType findingType,
        List<String> chapterKeys,
        String detail,
        LocalDateTime createdAt) {

    public OutlineFinding {
        if (skeletonId == null) {
            throw new IllegalArgumentException("소견은 뼈대에 속해야 합니다.");
        }
        if (findingType == null) {
            throw new IllegalArgumentException("소견 종류는 필수입니다.");
        }
        detail = Strings.requireText(detail, "소견 내용");
        chapterKeys = chapterKeys == null ? List.of() : List.copyOf(chapterKeys);
    }

    public static OutlineFinding of(
            Long skeletonId, OutlineFindingType findingType, List<String> chapterKeys, String detail) {
        return new OutlineFinding(null, skeletonId, findingType, chapterKeys, detail, null);
    }
}
