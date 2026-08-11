package com.mindplates.nextchapter.domain.generation.model;

import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;

/**
 * 생성 파이프라인의 단계. 실패가 <b>어디서</b> 났는지를 가리킨다.
 *
 * <p>{@link SkeletonStatus} 를 그대로 쓰지 않는 이유는 둘이 다른 것을 말하기 때문이다. 뼈대 상태는
 * "지금 어디까지 왔나"이고 실패 단계는 "무엇을 다시 돌려야 하나"다. 실패한 뼈대의 상태는 {@code FAILED}
 * 로 덮이므로, 상태만 남기면 재시도할 단계를 알 수 없다.
 */
public enum GenerationStage {
    /** ① 챕터 제목·요약·관계. */
    GRAPH(SkeletonStatus.GENERATING_GRAPH),

    /** ② 챕터별 개요. */
    OUTLINE(SkeletonStatus.GENERATING_OUTLINES),

    /** ③ 블록 문서 본문 — 챕터 단위다. */
    BODY(SkeletonStatus.GENERATING_BODIES),

    /** ④ 이미지·도표·영상·PPT. */
    ASSET(SkeletonStatus.GENERATING_ASSETS);

    private final SkeletonStatus status;

    GenerationStage(SkeletonStatus status) {
        this.status = status;
    }

    /** 이 단계를 다시 돌리려면 뼈대가 있어야 하는 상태. 운영자 재시도가 여기로 되돌린다. */
    public SkeletonStatus skeletonStatus() {
        return status;
    }

    /** 실패가 챕터 하나에 귀속되는 단계인지. 아니면 뼈대 단위 실패다. */
    public boolean isPerChapter() {
        return this == BODY || this == ASSET;
    }
}
