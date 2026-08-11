package com.mindplates.nextchapter.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.out.BudgetAlertPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiBudgetSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiUsagePort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiBudgetSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiUsagePort;
import com.mindplates.nextchapter.application.admin.view.AiBudgetView;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import com.mindplates.nextchapter.domain.admin.model.AiBudgetSettings;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiUsageRecord;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 상한이 지켜야 하는 것은 셋이다 — <b>호출 전에</b> 막고, 막은 사실을 <b>알리고</b>, 쓴 토큰을 <b>남긴다</b>.
 *
 * <p>셋 다 조용히 실패할 수 있는 지점이다. 사후 판정은 마지막 호출을 놓치고, 알리지 않으면 뼈대가 생성 중
 * 상태로 남은 이유를 아무도 모르며, 기록이 빠지면 다음 판정의 기준이 틀린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("예산 상한 판정")
class AiBudgetServiceTest {

    @Mock
    LoadAiBudgetSettingsPort loadAiBudgetSettingsPort;

    @Mock
    SaveAiBudgetSettingsPort saveAiBudgetSettingsPort;

    @Mock
    LoadAiUsagePort loadAiUsagePort;

    @Mock
    SaveAiUsagePort saveAiUsagePort;

    @Mock
    BudgetAlertPort budgetAlertPort;

    @InjectMocks
    AiBudgetService service;

    private void stubSettings(Long perSkeleton, Long daily) {
        when(loadAiBudgetSettingsPort.load()).thenReturn(new AiBudgetSettings(perSkeleton, daily, null, null, null));
    }

    private void stubUsage(long skeletonUsed, long dailyUsed) {
        when(loadAiUsagePort.totalTokensBySkeletonId(anyLong())).thenReturn(skeletonUsed);
        when(loadAiUsagePort.totalTokensOn(any())).thenReturn(dailyUsed);
    }

    @Nested
    @DisplayName("판정")
    class Require {

        @Test
        @DisplayName("상한 안이면 통과한다")
        void passesWithinBudget() {
            stubSettings(10000L, 100000L);
            stubUsage(5000, 20000);

            assertThatCode(() -> service.requireWithinBudget(5L, 1000)).doesNotThrowAnyException();
            verifyNoInteractions(budgetAlertPort);
        }

        @Test
        @DisplayName("뼈대당 상한을 넘으면 중단하고 알린다")
        void stopsOnPerSkeletonLimit() {
            stubSettings(10000L, 1000000L);
            stubUsage(9500, 0);

            assertThatThrownBy(() -> service.requireWithinBudget(5L, 1000))
                    .isInstanceOf(BudgetExceededException.class)
                    .hasMessageContaining("뼈대당");

            verify(budgetAlertPort).budgetExceeded(5L, "뼈대당", 9500L, 10000L);
        }

        @Test
        @DisplayName("일일 상한을 넘으면 중단하고 알린다")
        void stopsOnDailyLimit() {
            stubSettings(1000000L, 10000L);
            stubUsage(0, 9500);

            assertThatThrownBy(() -> service.requireWithinBudget(5L, 1000))
                    .isInstanceOf(BudgetExceededException.class)
                    .hasMessageContaining("일일");

            verify(budgetAlertPort).budgetExceeded(5L, "일일", 9500L, 10000L);
        }

        /**
         * 주제 매핑처럼 어느 뼈대의 책임도 아닌 호출이 있다. 그 호출에 뼈대당 상한을 적용하면 뼈대 ID 를
         * 억지로 붙여야 하고, 그러면 엉뚱한 뼈대의 예산이 깎인다. 일일 상한에는 그대로 포함된다.
         */
        @Test
        @DisplayName("뼈대 없는 호출은 뼈대당 상한을 조회하지도 않는다")
        void skipsPerSkeletonWhenUnattributed() {
            stubSettings(1L, 1000000L);
            when(loadAiUsagePort.totalTokensOn(any())).thenReturn(0L);

            assertThatCode(() -> service.requireWithinBudget(null, 5000)).doesNotThrowAnyException();

            verify(loadAiUsagePort, org.mockito.Mockito.never()).totalTokensBySkeletonId(any());
        }

        @Test
        @DisplayName("무제한이면 아무리 써도 통과한다")
        void unlimitedNeverStops() {
            stubSettings(null, null);
            stubUsage(Long.MAX_VALUE / 4, Long.MAX_VALUE / 4);

            assertThatCode(() -> service.requireWithinBudget(5L, Integer.MAX_VALUE))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("기록")
    class Record {

        @Test
        @DisplayName("단계·벤더·모델과 토큰을 그대로 남긴다")
        void recordsUsage() {
            ArgumentCaptor<AiUsageRecord> captor = ArgumentCaptor.forClass(AiUsageRecord.class);

            service.recordUsage(5L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5", 1200, 800);

            verify(saveAiUsagePort).record(captor.capture());
            AiUsageRecord saved = captor.getValue();
            assertThat(saved.skeletonId()).isEqualTo(5L);
            assertThat(saved.stage()).isEqualTo(AiStage.SKELETON_BODY);
            assertThat(saved.vendor()).isEqualTo(AiVendor.ANTHROPIC);
            assertThat(saved.model()).isEqualTo("claude-opus-5");
            assertThat(saved.totalTokens()).isEqualTo(2000);
        }

        /** "하루"의 경계가 일일 상한의 값을 바꾼다. JVM 기본 시간대는 UTC 라 그대로 쓰면 9시간 어긋난다. */
        @Test
        @DisplayName("사용 날짜는 KST 기준이다")
        void usesKoreanDate() {
            ArgumentCaptor<AiUsageRecord> captor = ArgumentCaptor.forClass(AiUsageRecord.class);

            service.recordUsage(5L, AiStage.EMBEDDING, AiVendor.VOYAGE, "voyage-3-large", 10, 0);

            verify(saveAiUsagePort).record(captor.capture());
            assertThat(captor.getValue().usageDate())
                    .isEqualTo(LocalDate.now(com.mindplates.nextchapter.common.time.AppTime.KST));
        }
    }

    @Nested
    @DisplayName("설정")
    class Settings {

        /** 상한만 보여주면 "지금 얼마나 남았나"를 알 수 없다. 조정 판단에는 둘이 함께 필요하다. */
        @Test
        @DisplayName("조회는 상한과 오늘 사용량을 함께 낸다")
        void currentIncludesUsage() {
            stubSettings(10000L, 100000L);
            when(loadAiUsagePort.totalTokensOn(any())).thenReturn(4200L);

            AiBudgetView view = service.current();

            assertThat(view.perSkeletonTokenLimit()).isEqualTo(10000L);
            assertThat(view.dailyTokenLimit()).isEqualTo(100000L);
            assertThat(view.dailyUsedTokens()).isEqualTo(4200L);
        }

        @Test
        @DisplayName("무제한(null)으로 바꿀 수 있다")
        void allowsUnlimited() {
            stubSettings(10000L, 100000L);
            when(saveAiBudgetSettingsPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            AiBudgetView view = service.update(null, 50000L, "admin");

            assertThat(view.perSkeletonTokenLimit()).isNull();
            assertThat(view.dailyTokenLimit()).isEqualTo(50000L);
            assertThat(view.updatedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("뼈대별 사용량을 조회한다")
        void readsSkeletonUsage() {
            when(loadAiUsagePort.totalTokensBySkeletonId(eq(5L))).thenReturn(12345L);

            assertThat(service.usedTokensBySkeleton(5L)).isEqualTo(12345L);
        }
    }
}
