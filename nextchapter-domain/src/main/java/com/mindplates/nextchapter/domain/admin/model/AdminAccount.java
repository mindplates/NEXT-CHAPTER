package com.mindplates.nextchapter.domain.admin.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 관리자 계정 — 자체 계정 인증의 주체.
 *
 * <p>{@code passwordHash} 를 들고 있지만 이 값이 응답으로 나가는 경로는 없어야 한다. 웹 DTO 는
 * 이 필드를 담지 않는다.
 */
public record AdminAccount(
        Long id,
        String email,
        String name,
        String passwordHash,
        AdminRole role,
        boolean enabled,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public AdminAccount {
        email = Strings.requireText(email, "이메일").toLowerCase(Locale.ROOT);
        if (!email.contains("@")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다: " + email);
        }
        name = Strings.requireMaxLength(Strings.requireText(name, "이름"), 120, "이름");
        passwordHash = Strings.requireText(passwordHash, "비밀번호");
        if (role == null) {
            throw new IllegalArgumentException("역할은 필수입니다.");
        }
    }

    public static AdminAccount create(String email, String name, String passwordHash, AdminRole role) {
        return new AdminAccount(null, email, name, passwordHash, role, true, null, null, null);
    }

    public AdminAccount withPasswordHash(String newHash) {
        return new AdminAccount(id, email, name, newHash, role, enabled, lastLoginAt, createdAt, updatedAt);
    }

    public AdminAccount withLastLoginAt(LocalDateTime at) {
        return new AdminAccount(id, email, name, passwordHash, role, enabled, at, createdAt, updatedAt);
    }

    public AdminAccount withEnabled(boolean value) {
        return new AdminAccount(id, email, name, passwordHash, role, value, lastLoginAt, createdAt, updatedAt);
    }
}
