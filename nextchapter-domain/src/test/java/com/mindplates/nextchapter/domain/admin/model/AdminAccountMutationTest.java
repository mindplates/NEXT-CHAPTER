package com.mindplates.nextchapter.domain.admin.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 계정 상태 변경")
class AdminAccountMutationTest {

    @Test
    @DisplayName("비밀번호 교체는 나머지를 그대로 둔다")
    void replacesOnlyPasswordHash() {
        AdminAccount account = account();

        AdminAccount changed = account.withPasswordHash("{bcrypt}new");

        assertThat(changed.passwordHash()).isEqualTo("{bcrypt}new");
        assertThat(changed.email()).isEqualTo(account.email());
        assertThat(changed.enabled()).isTrue();
    }

    @Test
    @DisplayName("비활성화는 계정을 지우지 않는다 — 감사 추적이 남아야 한다")
    void disableKeepsAccount() {
        AdminAccount disabled = account().withEnabled(false);

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("마지막 로그인 시각만 갱신된다")
    void recordsLastLogin() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 12, 9, 0);

        AdminAccount logged = account().withLastLoginAt(at);

        assertThat(logged.lastLoginAt()).isEqualTo(at);
        assertThat(logged.passwordHash()).isEqualTo("{bcrypt}hash");
    }

    private static AdminAccount account() {
        return new AdminAccount(
                1L, "admin@nextchapter.local", "관리자", "{bcrypt}hash", AdminRole.ADMIN, true, null, null, null);
    }
}
