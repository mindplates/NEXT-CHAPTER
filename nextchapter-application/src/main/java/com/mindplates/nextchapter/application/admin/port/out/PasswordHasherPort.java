package com.mindplates.nextchapter.application.admin.port.out;

/**
 * 비밀번호 해싱을 어댑터로 밀어내는 포트.
 *
 * <p>알고리즘 선택(BCrypt 강도 등)은 인프라 관심사고 교체 대상이다. 애플리케이션이 알고리즘을
 * 알면 교체할 때 유스케이스 코드가 함께 흔들린다.
 */
public interface PasswordHasherPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
