package com.mindplates.nextchapter.domain.admin.model;

/**
 * 수정안의 생애 주기.
 *
 * <p>검증 실패는 승인 대기열에 올리지 않는다 — 주장–출처가 일치하지 않는 본문을 운영자가 승인·자동
 * 반영할 수 있게 두면 검증 패스를 둔 의미가 없다.
 */
public enum RevisionProposalStatus {
    /** 검증을 통과했다. 승인 대기열에서 처리를 기다린다. */
    PENDING_APPROVAL,
    /** 검증 패스가 주장–출처 불일치를 발견했다. 자동 반영 대상이 아니다. */
    VERIFICATION_FAILED,
    /** 운영자가 승인해 반영됐다. */
    APPROVED,
    /** 자동 반영 모드에서 자동으로 승인됐다. 반영 시점만 다를 뿐 이력은 동일하게 남는다. */
    AUTO_APPROVED,
    /** 운영자가 기각했다. */
    REJECTED
}
