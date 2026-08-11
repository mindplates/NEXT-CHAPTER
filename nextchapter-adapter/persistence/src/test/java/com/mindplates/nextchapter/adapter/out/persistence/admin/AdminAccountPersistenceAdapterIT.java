package com.mindplates.nextchapter.adapter.out.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(AdminAccountPersistenceAdapter.class)
@DisplayName("관리자 계정 어댑터 (실제 PostgreSQL)")
class AdminAccountPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    AdminAccountPersistenceAdapter accounts;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("이메일로 조회하고 개수를 센다 — 최초 계정 생성 판정의 재료")
    void savesAndFindsByEmail() {
        accounts.save(AdminAccount.create("admin@nextchapter.local", "관리자", "{bcrypt}hash", AdminRole.ADMIN));

        assertThat(accounts.count()).isEqualTo(1);
        assertThat(accounts.findByEmail("admin@nextchapter.local"))
                .isPresent()
                .get()
                .extracting(AdminAccount::role)
                .isEqualTo(AdminRole.ADMIN);
    }

    @Test
    @DisplayName("같은 이메일로 두 계정을 만들 수 없다")
    void emailIsUnique() {
        accounts.save(AdminAccount.create("admin@nextchapter.local", "관리자", "{bcrypt}hash", AdminRole.ADMIN));

        assertThatThrownBy(() -> {
                    accounts.save(
                            AdminAccount.create("admin@nextchapter.local", "다른 관리자", "{bcrypt}h2", AdminRole.ADMIN));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("마지막 로그인 시각이 갱신된다")
    void updatesLastLoginAt() {
        AdminAccount saved =
                accounts.save(AdminAccount.create("admin@nextchapter.local", "관리자", "{bcrypt}hash", AdminRole.ADMIN));
        LocalDateTime loginAt = LocalDateTime.now();

        accounts.save(saved.withLastLoginAt(loginAt));

        assertThat(accounts.findById(saved.id()))
                .isPresent()
                .get()
                .extracting(AdminAccount::lastLoginAt)
                .isNotNull();
    }
}
