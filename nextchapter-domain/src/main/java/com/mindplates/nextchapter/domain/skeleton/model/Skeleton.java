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

    /**
     * 상태를 전이한다. 허용되지 않은 전이는 거절한다.
     *
     * <p>검증을 도메인에 두는 이유는 전이가 여러 곳에서 일어나기 때문이다 — 각 단계 컨슈머, 실패
     * 처리, 운영자의 재시도. 호출부마다 규칙을 복제하면 한 곳만 빠뜨려도 단계를 건너뛴 뼈대가 생기고,
     * 그 뼈대는 개요 없이 본문이 쓰인 상태로 published 에 도달할 수 있다.
     */
    public Skeleton transitionTo(SkeletonStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalArgumentException("허용되지 않은 상태 전이입니다: " + status + " → " + next);
        }
        return new Skeleton(id, topicId, next, createdAt, updatedAt);
    }

    /** 다음 생성 단계로. 파이프라인 컨슈머가 자기 단계를 끝내고 부르는 경로다. */
    public Skeleton advance() {
        return transitionTo(status.next());
    }

    public Skeleton fail() {
        return transitionTo(SkeletonStatus.FAILED);
    }

    public boolean isPublished() {
        return status == SkeletonStatus.PUBLISHED;
    }
}
