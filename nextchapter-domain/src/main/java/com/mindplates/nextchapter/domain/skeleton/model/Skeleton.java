package com.mindplates.nextchapter.domain.skeleton.model;

import java.time.LocalDateTime;

/**
 * 뼈대 — 주제 하나에 1:1. 사용자 수와 무관하게 주제 수만큼만 존재한다.
 *
 * <p>공용이라는 점이 이 제품의 축이다. 여러 사람이 같은 본문을 보기 때문에 "같은 지점에서
 * 틀렸다"를 겹쳐 볼 수 있고, 그래서 집단 루프가 성립한다.
 */
public record Skeleton(Long id, Long topicId, SkeletonStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public Skeleton {
        if (topicId == null) {
            throw new IllegalArgumentException("뼈대는 주제에 속해야 합니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("뼈대 상태는 필수입니다.");
        }
    }

    public static Skeleton start(Long topicId) {
        return new Skeleton(null, topicId, SkeletonStatus.GENERATING_GRAPH, null, null);
    }

    public Skeleton withStatus(SkeletonStatus next) {
        return new Skeleton(id, topicId, next, createdAt, updatedAt);
    }

    public boolean isPublished() {
        return status == SkeletonStatus.PUBLISHED;
    }
}
