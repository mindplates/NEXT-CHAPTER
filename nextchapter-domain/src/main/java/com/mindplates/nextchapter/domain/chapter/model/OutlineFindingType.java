package com.mindplates.nextchapter.domain.chapter.model;

/**
 * 개요 검사 소견의 종류.
 *
 * <p>둘 다 <b>본문 생성을 막지 않는다.</b> 겹침·누락 판정은 어림이고, 오탐 하나가 파이프라인을 세우면
 * 사전 생성의 이점이 사라진다. 기록으로 남겨 운영자 검수가 보게 한다.
 */
public enum OutlineFindingType {
    /** 두 챕터 이상이 같은 내용을 다룬다. 본문이 겹치면 "같은 지점"의 신호가 두 챕터로 갈린다. */
    DUPLICATE,

    /** 주제 범위에서 빠진 내용이 있다. 선행 관계가 가리키는 개념이 어느 챕터에도 없는 경우가 대표적이다. */
    GAP
}
