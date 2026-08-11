package com.mindplates.nextchapter.domain.admin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 계정")
class AdminAccountTest {

    @Test
    @DisplayName("이메일은 소문자로 정규화된다")
    void lowercasesEmail() {
        assertThat(AdminAccount.create("Admin@Example.COM", "관리자", "{bcrypt}hash", AdminRole.ADMIN)
                        .email())
                .isEqualTo("admin@example.com");
    }

    @Test
    @DisplayName("이메일 형식이 아니면 거절한다")
    void rejectsMalformedEmail() {
        assertThatThrownBy(() -> AdminAccount.create("admin", "관리자", "{bcrypt}hash", AdminRole.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일");
    }

    @Test
    @DisplayName("생성 직후에는 활성 상태다")
    void createdEnabled() {
        assertThat(AdminAccount.create("a@b.com", "관리자", "{bcrypt}hash", AdminRole.ADMIN)
                        .enabled())
                .isTrue();
    }

    @Test
    @DisplayName("권한 문자열에 ROLE_ 접두사가 붙는다")
    void authorityPrefix() {
        assertThat(AdminRole.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
    }
}
