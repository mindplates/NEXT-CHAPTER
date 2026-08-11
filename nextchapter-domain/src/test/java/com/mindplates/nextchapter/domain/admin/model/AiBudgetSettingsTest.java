package com.mindplates.nextchapter.domain.admin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("예산 상한")
class AiBudgetSettingsTest {

    private static AiBudgetSettings limits(Long perSkeleton, Long daily) {
        return new AiBudgetSettings(perSkeleton, daily, null, null, null);
    }

    @Nested
    @DisplayName("판정")
    class Allows {

        @Test
        @DisplayName("두 상한 안이면 허용한다")
        void allowsWithinBoth() {
            assertThat(limits(1000L, 5000L).allowsPerSkeleton(500, 400)).isTrue();
            assertThat(limits(1000L, 5000L).allowsDaily(3000, 400)).isTrue();
        }

        /** 사후 판정만 하면 마지막 호출이 상한을 얼마든지 넘길 수 있다. 사전 생성은 그 한 번이 큰 호출이다. */
        @Test
        @DisplayName("이번 호출의 상한값을 더해서 판정한다 — 넘길 <em>수 있으면</em> 막는다")
        void countsRequestedTokens() {
            AiBudgetSettings settings = limits(1000L, 100000L);

            assertThat(settings.allowsPerSkeleton(900, 100)).isTrue();
            assertThat(settings.allowsPerSkeleton(900, 101)).isFalse();
        }

        @Test
        @DisplayName("뼈대당 상한만 넘어도 막는다")
        void blocksOnPerSkeleton() {
            assertThat(limits(1000L, 1000000L).allowsPerSkeleton(999, 10)).isFalse();
        }

        @Test
        @DisplayName("일일 상한만 넘어도 막는다")
        void blocksOnDaily() {
            assertThat(limits(1000000L, 1000L).allowsDaily(999, 10)).isFalse();
        }

        /** 무제한을 허용하는 것은 설계 결정이다 — 운영자가 필요하면 끌 수 있어야 한다. */
        @Test
        @DisplayName("null 은 무제한이다")
        void nullMeansUnlimited() {
            assertThat(limits(null, null).allowsPerSkeleton(Long.MAX_VALUE / 2, Integer.MAX_VALUE))
                    .isTrue();
            assertThat(limits(null, null).allowsDaily(Long.MAX_VALUE / 2, Integer.MAX_VALUE))
                    .isTrue();
            assertThat(limits(null, null).isPerSkeletonUnlimited()).isTrue();
            assertThat(limits(null, null).isDailyUnlimited()).isTrue();
        }

        @Test
        @DisplayName("한쪽만 무제한일 수 있다")
        void allowsOneSidedUnlimited() {
            assertThat(limits(null, 1000L).allowsPerSkeleton(Long.MAX_VALUE / 2, 10))
                    .isTrue();
            assertThat(limits(null, 1000L).allowsDaily(1000, 10)).isFalse();
        }
    }

    @Nested
    @DisplayName("검증")
    class Validation {

        @Test
        @DisplayName("0 이하 상한은 거절한다 — 무제한은 null 로 표현한다")
        void rejectsNonPositive() {
            assertThatThrownBy(() -> limits(0L, 100L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> limits(100L, -1L)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("상한 변경은 새 객체를 만든다")
        void withLimitsIsImmutable() {
            AiBudgetSettings original = limits(1000L, 5000L);

            AiBudgetSettings changed = original.withLimits(null, 9000L, "admin");

            assertThat(original.perSkeletonTokenLimit()).isEqualTo(1000L);
            assertThat(changed.isPerSkeletonUnlimited()).isTrue();
            assertThat(changed.dailyTokenLimit()).isEqualTo(9000L);
            assertThat(changed.updatedBy()).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("사용량 기록")
    class Usage {

        @Test
        @DisplayName("입력·출력 토큰을 합쳐 센다 — 둘 다 청구된다")
        void sumsBothDirections() {
            AiUsageRecord record = AiUsageRecord.of(
                    5L,
                    AiStage.SKELETON_BODY,
                    AiVendor.ANTHROPIC,
                    "claude-opus-5",
                    1200,
                    800,
                    java.time.LocalDate.of(2026, 8, 12));

            assertThat(record.totalTokens()).isEqualTo(2000);
        }

        @Test
        @DisplayName("뼈대에 귀속되지 않는 호출도 기록한다 — 일일 상한에 포함된다")
        void allowsNullSkeleton() {
            AiUsageRecord record = AiUsageRecord.of(
                    null,
                    AiStage.TOPIC_MAPPING,
                    AiVendor.ANTHROPIC,
                    "claude-opus-5",
                    50,
                    10,
                    java.time.LocalDate.of(2026, 8, 12));

            assertThat(record.skeletonId()).isNull();
            assertThat(record.totalTokens()).isEqualTo(60);
        }

        @Test
        @DisplayName("음수 토큰과 빈 모델은 거절한다")
        void rejectsInvalid() {
            java.time.LocalDate date = java.time.LocalDate.of(2026, 8, 12);

            assertThatThrownBy(() -> AiUsageRecord.of(1L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "m", -1, 0, date))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AiUsageRecord.of(1L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, " ", 1, 0, date))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AiUsageRecord.of(1L, null, AiVendor.ANTHROPIC, "m", 1, 0, date))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AiUsageRecord.of(1L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "m", 1, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
