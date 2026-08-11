package com.mindplates.nextchapter.domain.admin.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 예산 상한. 뼈대당과 일일을 <b>이중으로</b> 둔다.
 *
 * <p>뼈대당 상한은 범위가 넘스러운 주제 하나가 예산을 혼자 태우는 것을 막고, 일일 상한은 전체 폭주를
 * 막는다. 하나로는 부족하다 — 뼈대당만 있으면 뼈대 100개가 동시에 돌 때 막지 못하고, 일일만 있으면
 * 뼈대 하나가 그날 예산을 다 먹은 뒤에야 멈춘다.
 *
 * @param perSkeletonTokenLimit null 이면 무제한
 * @param dailyTokenLimit null 이면 무제한
 */
public record AiBudgetSettings(
        Long perSkeletonTokenLimit,
        Long dailyTokenLimit,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public AiBudgetSettings {
        if (perSkeletonTokenLimit != null && perSkeletonTokenLimit < 1) {
            throw new IllegalArgumentException("뼈대당 상한은 1 이상이거나 무제한(null)이어야 합니다.");
        }
        if (dailyTokenLimit != null && dailyTokenLimit < 1) {
            throw new IllegalArgumentException("일일 상한은 1 이상이거나 무제한(null)이어야 합니다.");
        }
        updatedBy = Strings.trimToNull(updatedBy);
    }

    public boolean isPerSkeletonUnlimited() {
        return perSkeletonTokenLimit == null;
    }

    public boolean isDailyUnlimited() {
        return dailyTokenLimit == null;
    }

    /**
     * 두 상한을 <b>따로</b> 판정한다. 하나로 합치지 않는 이유는 두 가지다.
     *
     * <p>첫째, 뼈대에 귀속되지 않는 호출(주제 매핑·임베딩)에는 뼈대당 상한을 적용하지 않는다. 합친
     * 판정에 {@code skeletonUsed = 0} 을 넘겨 우회하려 하면 <b>상한 자체가 작을 때 막힌다</b> — 사용량이
     * 0이어도 {@code 0 + maxTokens > 상한} 이면 거절되고, 그 호출은 어느 뼈대의 책임도 아닌데 뼈대당
     * 상한 때문에 실패한다.
     *
     * <p>둘째, 어느 상한에 걸렸는지가 알림과 메시지에 필요하다. 합친 결과에서 역추적하면 판정 조건을
     * 두 곳에 쓰게 되고, 그 둘이 어긋나면 "일일 상한"이라고 알리면서 실제로는 뼈대당에서 막힌다.
     *
     * <p>둘 다 <b>이번 호출의 상한값</b>({@code maxTokens})을 더해 본다. 호출 전에는 실제 소비량을 알 수
     * 없으므로 상한을 상한선으로 쓰는 것이 유일하게 보수적인 방법이다 — 사후 판정만 하면 마지막 호출이
     * 상한을 얼마든지 넘길 수 있고, 사전 생성은 그 한 번이 큰 호출이다.
     */
    public boolean allowsPerSkeleton(long skeletonUsed, int maxTokens) {
        return isPerSkeletonUnlimited() || skeletonUsed + maxTokens <= perSkeletonTokenLimit;
    }

    public boolean allowsDaily(long dailyUsed, int maxTokens) {
        return isDailyUnlimited() || dailyUsed + maxTokens <= dailyTokenLimit;
    }

    public AiBudgetSettings withLimits(Long newPerSkeleton, Long newDaily, String actor) {
        return new AiBudgetSettings(newPerSkeleton, newDaily, actor, createdAt, updatedAt);
    }
}
