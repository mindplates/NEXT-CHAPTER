package com.mindplates.nextchapter.domain.layer.model;

import java.time.LocalDateTime;

/**
 * 한 사용자의 한 챕터에 대한 이해도 — <b>신호에서 파생한다.</b>
 *
 * <p>집계 테이블을 따로 두지 않은 이유는 이중 기록을 만들지 않기 위해서다. 신호가 원천이고 이 값이 그
 * 투영이므로, 둘을 나란히 저장하면 어긋날 수 있고 그 어긋남은 <b>조용히 잘못된 추천</b>으로 나타난다.
 * 규모가 커져 조회가 무거워지면 그때 집계 테이블을 붙인다 — 그 시점에도 원천은 신호다.
 *
 * <p><b>챕터 ID 기준</b>이다. 뼈대가 수정돼 버전이 올라가도 신호는 그 챕터에 계속 붙으므로 개인 학습
 * 기록이 살아남는다.
 *
 * @param attempts 퀴즈 시도 수
 * @param correct 맞힌 수. 서버가 채점한 값만 들어온다
 * @param questions 질문 수. <b>사용자의 부족함이 아니라 콘텐츠의 결함 신고</b>이지만, 개인 루프에서는
 *     "이 사람이 여기서 막혔다"의 신호로도 읽는다
 * @param progressPercent 소비 진행. 같은 챕터에서 여러 번 왔다면 가장 큰 값이다
 */
public record ChapterUnderstanding(
        Long chapterId, int attempts, int correct, int questions, int progressPercent, LocalDateTime lastSeenAt) {

    /** 이 값 아래면 막힌 것으로 본다. */
    private static final double STRUGGLING_RATE = 0.5;

    /** 이 값 이상이면 충분히 이해한 것으로 본다. */
    private static final double CONFIDENT_RATE = 0.8;

    /** 구간 판정에 필요한 최소 시도 수. 한 문항으로 이해도를 정하면 운이 이해도가 된다. */
    private static final int MIN_ATTEMPTS = 2;

    public ChapterUnderstanding {
        if (chapterId == null) {
            throw new IllegalArgumentException("이해도는 챕터에 붙는다.");
        }
        if (attempts < 0 || correct < 0 || questions < 0) {
            throw new IllegalArgumentException("신호 수는 음수가 될 수 없습니다.");
        }
        if (correct > attempts) {
            throw new IllegalArgumentException("맞힌 수가 시도 수보다 클 수 없습니다.");
        }
        progressPercent = Math.clamp(progressPercent, 0, 100);
    }

    public static ChapterUnderstanding none(Long chapterId) {
        return new ChapterUnderstanding(chapterId, 0, 0, 0, 0, null);
    }

    public boolean visited() {
        return lastSeenAt != null;
    }

    public boolean completed() {
        return progressPercent >= 100;
    }

    public double correctRate() {
        return attempts == 0 ? 0 : (double) correct / attempts;
    }

    /**
     * 구간 판정.
     *
     * <p>시도가 적으면 {@link UnderstandingLevel#UNKNOWN} 이다 — <b>모르는 것과 낮은 것을 구분한다.</b>
     * 합치면 처음 온 사용자를 "막힌 사람"으로 취급해 선행 챕터로 되돌리게 되고, 그건 진입 자체를 막는다.
     *
     * <p>질문이 시도보다 많으면 막힌 것으로 본다. 퀴즈를 풀기 전에 이미 여러 번 물었다는 뜻이고, 그때 정답률은
     * 아직 아무것도 말해 주지 않는다.
     */
    public UnderstandingLevel level() {
        if (questions >= MIN_ATTEMPTS && questions > attempts) {
            return UnderstandingLevel.STRUGGLING;
        }
        if (attempts < MIN_ATTEMPTS) {
            return UnderstandingLevel.UNKNOWN;
        }
        double rate = correctRate();
        if (rate < STRUGGLING_RATE) {
            return UnderstandingLevel.STRUGGLING;
        }
        return rate >= CONFIDENT_RATE ? UnderstandingLevel.CONFIDENT : UnderstandingLevel.LEARNING;
    }
}
