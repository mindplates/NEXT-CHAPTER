package com.mindplates.nextchapter.adapter.out.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.admin.model.AiBudgetSettings;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiUsageRecord;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 예산 판정은 <b>합계 쿼리</b>가 맞아야 성립한다. 목으로 덮으면 정작 틀릴 곳 — 행이 없을 때의
 * {@code SUM}, 뼈대에 귀속되지 않는 행의 취급, 날짜 경계 — 이 검증되지 않는다.
 */
@Import({AiUsagePersistenceAdapter.class, AiBudgetSettingsPersistenceAdapter.class})
@DisplayName("예산 어댑터 (실제 PostgreSQL)")
class AiBudgetPersistenceAdapterIT extends AbstractPostgresIT {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Autowired
    AiUsagePersistenceAdapter usage;

    @Autowired
    AiBudgetSettingsPersistenceAdapter settings;

    @Autowired
    EntityManager entityManager;

    private void record(Long skeletonId, int in, int out, LocalDate date) {
        usage.record(AiUsageRecord.of(
                skeletonId, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5", in, out, date));
        entityManager.flush();
    }

    /** 첫 호출은 항상 이 상태다. {@code SUM} 이 null 을 주므로 예외 경로가 아니라 정상 경로다. */
    @Test
    @DisplayName("사용 기록이 없으면 합계가 0이다")
    void sumsToZeroWhenEmpty() {
        assertThat(usage.totalTokensBySkeletonId(9999L)).isZero();
        assertThat(usage.totalTokensOn(LocalDate.of(1999, 1, 1))).isZero();
    }

    @Test
    @DisplayName("뼈대별 합계는 입력과 출력을 함께 센다")
    void sumsBothDirectionsPerSkeleton() {
        record(101L, 1000, 500, TODAY);
        record(101L, 200, 100, TODAY);
        record(102L, 9999, 9999, TODAY);

        assertThat(usage.totalTokensBySkeletonId(101L)).isEqualTo(1800);
    }

    /**
     * 뼈대에 귀속되지 않는 호출(주제 매핑·임베딩)은 <b>일일 합계에만</b> 들어간다. 뼈대당 합계에 넣으면
     * 엉뚱한 뼈대의 예산이 깎이고, 일일 합계에서 빼면 전체 폭주를 막는 장치에 구멍이 난다.
     */
    @Test
    @DisplayName("뼈대 없는 호출은 일일 합계에만 들어간다")
    void unattributedCountsTowardDailyOnly() {
        record(201L, 100, 0, TODAY);
        record(null, 50, 0, TODAY);

        assertThat(usage.totalTokensOn(TODAY)).isEqualTo(150);
        assertThat(usage.totalTokensBySkeletonId(201L)).isEqualTo(100);
    }

    /** 날짜가 섞이면 일일 상한이 사실상 누적 상한이 된다 — 하루가 지나도 예산이 돌아오지 않는다. */
    @Test
    @DisplayName("일일 합계는 그 날짜만 센다")
    void dailySumIsScopedToDate() {
        record(301L, 700, 0, TODAY);
        record(301L, 5000, 0, YESTERDAY);

        assertThat(usage.totalTokensOn(TODAY)).isEqualTo(700);
        assertThat(usage.totalTokensBySkeletonId(301L)).isEqualTo(5700);
    }

    /** 상한이 자바 상수가 아니라 마이그레이션이 심는 행이어야 관리 화면의 변경이 실제로 반영된다. */
    @Test
    @DisplayName("마이그레이션이 상한 기본값을 심어 둔다")
    void migrationSeedsLimits() {
        AiBudgetSettings loaded = settings.load();

        assertThat(loaded.isPerSkeletonUnlimited()).isFalse();
        assertThat(loaded.isDailyUnlimited()).isFalse();
        assertThat(loaded.perSkeletonTokenLimit()).isPositive();
        assertThat(loaded.dailyTokenLimit()).isGreaterThan(loaded.perSkeletonTokenLimit());
    }

    @Test
    @DisplayName("무제한으로 바꿔도 행이 하나로 유지된다")
    void updateKeepsSingleRow() {
        settings.save(settings.load().withLimits(null, 12345L, "admin"));
        entityManager.flush();
        entityManager.clear();

        AiBudgetSettings loaded = settings.load();
        assertThat(loaded.isPerSkeletonUnlimited()).isTrue();
        assertThat(loaded.dailyTokenLimit()).isEqualTo(12345L);
        assertThat(loaded.updatedBy()).isEqualTo("admin");
        assertThat(entityManager
                        .createQuery("SELECT COUNT(s) FROM AiBudgetSettingsJpaEntity s", Long.class)
                        .getSingleResult())
                .isEqualTo(1L);
    }

    /**
     * 설정 행이 둘이면 어느 것이 유효한지 알 수 없고, 그 상태는 조회 순서에 따라 다르게 동작한다.
     * 애플리케이션 검사만으로는 동시 요청에서 조용히 통과하므로 DB {@code CHECK} 로 막는다.
     */
    @Test
    @DisplayName("두 번째 설정 행은 DB 가 거절한다")
    void rejectsSecondSettingsRow() {
        assertThatThrownBy(() -> {
                    entityManager
                            .createNativeQuery(
                                    "INSERT INTO ai_budget_settings (id, daily_token_limit, created_at, updated_at)"
                                            + " VALUES (2, 100, NOW(), NOW())")
                            .executeUpdate();
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }
}
