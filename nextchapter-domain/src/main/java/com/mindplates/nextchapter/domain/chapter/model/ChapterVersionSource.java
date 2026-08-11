package com.mindplates.nextchapter.domain.chapter.model;

/**
 * 이 버전이 왜 생겼는지.
 *
 * <p>버전마다 출처를 남기는 이유는 감사 추적이다 — AI 가 생성한 콘텐츠를 AI 가 다시 고치는 구조에서
 * "언제 무엇이 왜 바뀌었는가"가 유일한 되돌리기 수단이다.
 */
public enum ChapterVersionSource {
    /** 최초 생성 (생성 파이프라인 ③ 본문 생성). */
    GENERATED,
    /** 집단 신호가 임계치를 넘어 수정됐다. */
    REVISED_BY_SIGNAL,
    /** 운영자가 검수·피드백으로 직접 수정했다. */
    REVISED_BY_ADMIN,
    /** 검증 패스가 사실 오류를 고쳤다. */
    REVISED_BY_VERIFICATION
}
