package com.mindplates.nextchapter.adapter.security;

import com.mindplates.nextchapter.application.admin.port.out.PasswordHasherPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 해싱 어댑터.
 *
 * <p>{@link PasswordEncoder} 를 {@code DelegatingPasswordEncoder} 로 받으므로 저장된 해시에
 * 알고리즘 접두사({@code {bcrypt}})가 붙는다. 나중에 알고리즘을 올릴 때 기존 해시를 그대로 두고
 * 새로 저장되는 것만 바뀌게 하려면 이 접두사가 있어야 한다 — 없으면 전체 계정 비밀번호 재설정이
 * 유일한 이행 경로가 된다.
 */
@Component
public class BcryptPasswordHasher implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    public BcryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
