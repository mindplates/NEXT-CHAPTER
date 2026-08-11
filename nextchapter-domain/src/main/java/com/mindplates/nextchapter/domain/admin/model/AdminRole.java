package com.mindplates.nextchapter.domain.admin.model;

/**
 * 관리자 역할. 지금은 하나뿐이다.
 *
 * <p>검수자·운영자 분리는 아직 정하지 않았다(설계 문서의 미결정 항목). 그래도 역할을 값으로
 * 들고 있는 이유는, 나중에 컬럼을 추가하는 것보다 값을 추가하는 것이 훨씬 싸기 때문이다.
 */
public enum AdminRole {
    ADMIN;

    /** Spring Security 권한 문자열. {@code ROLE_} 접두사 규칙을 도메인 밖으로 새게 두지 않는다. */
    public String authority() {
        return "ROLE_" + name();
    }
}
