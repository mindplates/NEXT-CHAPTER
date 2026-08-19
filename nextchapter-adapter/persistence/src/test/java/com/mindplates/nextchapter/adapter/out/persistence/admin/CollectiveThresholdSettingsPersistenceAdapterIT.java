package com.mindplates.nextchapter.adapter.out.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.admin.model.CollectiveThresholdSettings;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 예산 설정과 같은 단일 행 패턴이다 — 여기서만 확인되는 것은 마이그레이션 기본값과 행이 하나로 유지되는지. */
@Import(CollectiveThresholdSettingsPersistenceAdapter.class)
@DisplayName("집단 루프 임계치 설정 어댑터 (실제 PostgreSQL)")
class CollectiveThresholdSettingsPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    CollectiveThresholdSettingsPersistenceAdapter settings;

    @Autowired
    EntityManager entityManager;

    /** CLAUDE.md 예시 값이 상수가 아니라 마이그레이션이 심는 행이어야 관리 화면의 변경이 실제로 반영된다. */
    @Test
    @DisplayName("마이그레이션이 기본 임계치를 심어 둔다")
    void migrationSeedsDefaults() {
        CollectiveThresholdSettings loaded = settings.load();

        assertThat(loaded.questionThreshold()).isEqualTo(5);
        assertThat(loaded.minAttempts()).isEqualTo(20);
        assertThat(loaded.wrongRatePercent()).isEqualTo(40);
    }

    @Test
    @DisplayName("변경해도 행이 하나로 유지된다")
    void updateKeepsSingleRow() {
        settings.save(settings.load().withValues(10, 30, 50, "admin"));
        entityManager.flush();
        entityManager.clear();

        CollectiveThresholdSettings loaded = settings.load();
        assertThat(loaded.questionThreshold()).isEqualTo(10);
        assertThat(loaded.minAttempts()).isEqualTo(30);
        assertThat(loaded.wrongRatePercent()).isEqualTo(50);
        assertThat(loaded.updatedBy()).isEqualTo("admin");
        assertThat(entityManager
                        .createQuery("SELECT COUNT(s) FROM CollectiveThresholdSettingsJpaEntity s", Long.class)
                        .getSingleResult())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("두 번째 설정 행은 DB 가 거절한다")
    void rejectsSecondSettingsRow() {
        assertThatThrownBy(() -> {
                    entityManager
                            .createNativeQuery("INSERT INTO collective_threshold_settings"
                                    + " (id, question_threshold, min_attempts, wrong_rate_percent, created_at, updated_at)"
                                    + " VALUES (2, 5, 20, 40, NOW(), NOW())")
                            .executeUpdate();
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }
}
