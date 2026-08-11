package com.mindplates.nextchapter.domain.chapter.model;

import java.time.LocalDateTime;

/** 관계 하나. 방향이 있다 — {@code from} 에서 {@code to} 로 읽는다. */
public record ChapterRelation(
        Long id,
        Long skeletonId,
        Long fromChapterId,
        Long toChapterId,
        ChapterRelationType relationType,
        LocalDateTime createdAt) {

    public ChapterRelation {
        if (skeletonId == null || fromChapterId == null || toChapterId == null) {
            throw new IllegalArgumentException("관계는 뼈대와 양쪽 챕터를 모두 가리켜야 합니다.");
        }
        if (fromChapterId.equals(toChapterId)) {
            throw new IllegalArgumentException("자기 자신에 대한 관계는 만들 수 없습니다. chapterId=" + fromChapterId);
        }
        if (relationType == null) {
            throw new IllegalArgumentException("관계 타입은 필수입니다.");
        }
    }

    public static ChapterRelation of(
            Long skeletonId, Long fromChapterId, Long toChapterId, ChapterRelationType relationType) {
        return new ChapterRelation(null, skeletonId, fromChapterId, toChapterId, relationType, null);
    }

    public boolean isPrerequisite() {
        return relationType == ChapterRelationType.PREREQUISITE;
    }
}
