package com.mindplates.nextchapter.application.admin.port.in;

/**
 * 첫 관리자 계정을 만든다 — 계정이 하나도 없을 때만.
 *
 * <p>이 경로가 필요한 이유는 순환이다. 관리자 계정은 관리 화면에서 만들고, 관리 화면은 관리자
 * 인증을 통과해야 열린다. 어딘가에서 한 번은 끊어야 하고, 그 지점을 "계정이 0개일 때"로 좁히는
 * 것이 가장 작은 구멍이다.
 */
public interface EnsureBootstrapAdminUseCase {

    /** @return 새로 만들었으면 true, 이미 계정이 있어 건너뛰었으면 false */
    boolean ensureBootstrapAdmin(String email, String name, String rawPassword);
}
