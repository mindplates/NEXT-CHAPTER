package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;

/** 키로 표현된 관계 제안. 저장 후 키→ID 로 옮겨진다. */
public record ProposedRelation(String fromKey, String toKey, ChapterRelationType relationType) {

    public ProposedRelation {
        fromKey = Strings.requireText(fromKey, "출발 챕터 키");
        toKey = Strings.requireText(toKey, "도착 챕터 키");
        if (relationType == null) {
            throw new IllegalArgumentException("관계 타입은 필수입니다.");
        }
    }
}
