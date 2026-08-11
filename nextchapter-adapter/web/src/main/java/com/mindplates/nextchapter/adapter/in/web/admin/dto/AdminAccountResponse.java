package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import java.time.LocalDateTime;

/**
 * 관리자 계정 응답.
 *
 * <p>{@code passwordHash} 가 없는 것이 이 DTO 의 존재 이유다. 도메인 모델을 그대로 직렬화하면
 * 해시가 응답에 실려 나가고, 그건 필드를 하나 안 지운 실수만으로 일어난다.
 */
public record AdminAccountResponse(
        Long id,
        String email,
        String name,
        AdminRole role,
        boolean enabled,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {

    public static AdminAccountResponse from(AdminAccount account) {
        return new AdminAccountResponse(
                account.id(),
                account.email(),
                account.name(),
                account.role(),
                account.enabled(),
                account.lastLoginAt(),
                account.createdAt());
    }
}
