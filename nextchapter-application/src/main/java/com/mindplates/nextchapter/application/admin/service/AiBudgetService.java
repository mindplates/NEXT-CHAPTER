package com.mindplates.nextchapter.application.admin.service;

import com.mindplates.nextchapter.application.admin.port.in.GetAiBudgetUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateAiBudgetUseCase;
import com.mindplates.nextchapter.application.admin.port.out.BudgetAlertPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiBudgetSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiUsagePort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiBudgetSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiUsagePort;
import com.mindplates.nextchapter.application.admin.view.AiBudgetView;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import com.mindplates.nextchapter.common.time.AppTime;
import com.mindplates.nextchapter.domain.admin.model.AiBudgetSettings;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiUsageRecord;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예산 상한 판정과 사용량 기록.
 *
 * <p>판정을 <b>호출 전에</b> 한다. 사후 판정만 하면 마지막 호출이 상한을 얼마든지 넘길 수 있고, 사전 생성은
 * 그 한 번이 큰 호출이다. 호출 전에는 실제 소비량을 알 수 없으므로 {@code maxTokens} 를 상한선으로 쓴다 —
 * 보수적으로 막는 쪽이 예산을 넘기는 쪽보다 낫다.
 *
 * <p>기록은 <b>별도 트랜잭션</b>으로 커밋한다({@code REQUIRES_NEW}). 생성이 실패해 롤백되더라도 그 호출에
 * 쓴 토큰은 실제로 청구되므로, 사용량이 함께 롤백되면 예산이 조용히 새어 나간다.
 */
@Service
@RequiredArgsConstructor
public class AiBudgetService implements GetAiBudgetUseCase, UpdateAiBudgetUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiBudgetService.class);

    private final LoadAiBudgetSettingsPort loadAiBudgetSettingsPort;
    private final SaveAiBudgetSettingsPort saveAiBudgetSettingsPort;
    private final LoadAiUsagePort loadAiUsagePort;
    private final SaveAiUsagePort saveAiUsagePort;
    private final BudgetAlertPort budgetAlertPort;

    /**
     * 상한을 넘으면 {@link BudgetExceededException} 을 던진다.
     *
     * @param skeletonId null 이면 뼈대당 상한은 검사하지 않는다 — 주제 매핑처럼 어느 뼈대의 책임도 아닌 호출이다
     * @param maxTokens 이번 호출이 쓸 수 있는 최대 토큰
     */
    @Transactional(readOnly = true)
    public void requireWithinBudget(Long skeletonId, int maxTokens) {
        AiBudgetSettings settings = loadAiBudgetSettingsPort.load();

        if (skeletonId != null) {
            long skeletonUsed = loadAiUsagePort.totalTokensBySkeletonId(skeletonId);
            if (!settings.allowsPerSkeleton(skeletonUsed, maxTokens)) {
                reject(skeletonId, "뼈대당", skeletonUsed, settings.perSkeletonTokenLimit(), maxTokens);
            }
        }

        long dailyUsed = loadAiUsagePort.totalTokensOn(today());
        if (!settings.allowsDaily(dailyUsed, maxTokens)) {
            reject(skeletonId, "일일", dailyUsed, settings.dailyTokenLimit(), maxTokens);
        }
    }

    private void reject(Long skeletonId, String scope, long used, long limit, int maxTokens) {
        budgetAlertPort.budgetExceeded(skeletonId, scope, used, limit);
        throw new BudgetExceededException(
                scope + " 토큰 상한을 넘어 생성을 중단합니다. 사용=" + used + " 상한=" + limit + " 요청=" + maxTokens);
    }

    /** 실패로 롤백되더라도 쓴 토큰은 청구된다 — 사용량이 함께 롤백되면 예산이 조용히 새어 나간다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordUsage(
            Long skeletonId, AiStage stage, AiVendor vendor, String model, int inputTokens, int outputTokens) {
        saveAiUsagePort.record(AiUsageRecord.of(skeletonId, stage, vendor, model, inputTokens, outputTokens, today()));
    }

    @Override
    @Transactional(readOnly = true)
    public AiBudgetView current() {
        AiBudgetSettings settings = loadAiBudgetSettingsPort.load();
        return new AiBudgetView(
                settings.perSkeletonTokenLimit(),
                settings.dailyTokenLimit(),
                loadAiUsagePort.totalTokensOn(today()),
                settings.updatedBy());
    }

    @Override
    @Transactional(readOnly = true)
    public long usedTokensBySkeleton(Long skeletonId) {
        return loadAiUsagePort.totalTokensBySkeletonId(skeletonId);
    }

    @Override
    @Transactional
    public AiBudgetView update(Long perSkeletonTokenLimit, Long dailyTokenLimit, String actor) {
        AiBudgetSettings saved = saveAiBudgetSettingsPort.save(
                loadAiBudgetSettingsPort.load().withLimits(perSkeletonTokenLimit, dailyTokenLimit, actor));
        log.info(
                "[예산] 상한 변경 뼈대당={} 일일={} actor={}",
                saved.isPerSkeletonUnlimited() ? "무제한" : saved.perSkeletonTokenLimit(),
                saved.isDailyUnlimited() ? "무제한" : saved.dailyTokenLimit(),
                actor);
        return new AiBudgetView(
                saved.perSkeletonTokenLimit(),
                saved.dailyTokenLimit(),
                loadAiUsagePort.totalTokensOn(today()),
                saved.updatedBy());
    }

    /** "하루"의 경계를 KST 로 고정한다 — JVM 기본 시간대는 UTC 라 그대로 쓰면 경계가 9시간 어긋난다. */
    private static LocalDate today() {
        return LocalDate.now(AppTime.KST);
    }
}
