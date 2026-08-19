package com.mindplates.nextchapter.domain.admin.model;

import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import java.time.LocalDateTime;

/**
 * 집단 루프 임계치. 낮은 절대치로 시작하고 설정값으로 뺀다 — 시드 사용자가 수십 명일 때도 루프가 한
 * 바퀴 돌아야 한다.
 *
 * <p>비율 기반이 아닌 이유는 초기 표본이 작아서다. 소비자가 3명일 때 1명만 물어도 33% 가 되어
 * 오작동한다. {@code minAttempts} 로 표본 크기를 먼저 확보한 뒤에만 오답률을 본다.
 *
 * @param questionThreshold 같은 블록에 이 건수 이상 질문이 모이면 트리거
 * @param minAttempts 오답률을 보기 시작하는 최소 시도 횟수
 * @param wrongRatePercent 이 퍼센트를 초과하면 트리거(시도 횟수가 {@code minAttempts} 이상일 때만)
 */
public record CollectiveThresholdSettings(
        int questionThreshold,
        int minAttempts,
        int wrongRatePercent,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public CollectiveThresholdSettings {
        if (questionThreshold < 1) {
            throw new IllegalArgumentException("질문 임계치는 1 이상이어야 합니다: " + questionThreshold);
        }
        if (minAttempts < 1) {
            throw new IllegalArgumentException("최소 시도 횟수는 1 이상이어야 합니다: " + minAttempts);
        }
        if (wrongRatePercent < 1 || wrongRatePercent > 100) {
            throw new IllegalArgumentException("오답률 임계치는 1~100 이어야 합니다: " + wrongRatePercent);
        }
        updatedBy = Strings.trimToNull(updatedBy);
    }

    /**
     * 질문 건수와 오답률을 <b>독립적으로</b> 판정한다. 질문이 몰리는 지점과 오답이 몰리는 지점은
     * 서로 다른 결함일 수 있고, 하나로 합치면 어느 쪽이 신호였는지가 알림에서 사라진다.
     */
    public boolean isTriggered(BlockSignalAggregate aggregate) {
        boolean questionSpike = aggregate.questionCount() >= questionThreshold;
        boolean wrongRateSpike =
                aggregate.attemptCount() >= minAttempts && aggregate.wrongRate() * 100 > wrongRatePercent;
        return questionSpike || wrongRateSpike;
    }

    public CollectiveThresholdSettings withValues(
            int newQuestionThreshold, int newMinAttempts, int newWrongRatePercent, String actor) {
        return new CollectiveThresholdSettings(
                newQuestionThreshold, newMinAttempts, newWrongRatePercent, actor, createdAt, updatedAt);
    }
}
