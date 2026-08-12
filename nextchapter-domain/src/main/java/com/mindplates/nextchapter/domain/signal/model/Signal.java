package com.mindplates.nextchapter.domain.signal.model;

import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 신호 하나. 소비 과정에서 일어난 사실이고, <b>고쳐 쓰지 않는다.</b>
 *
 * <p>같은 레코드가 두 루프에 쓰인다 — 개인 루프는 이 행을 즉시 읽고, 집단 루프는 이 행을 Kafka 로
 * 흘려 집계한다. 두 벌로 나누지 않는 이유는 "같은 신호를 두 방향으로 쓴다"가 설계이기 때문이다.
 *
 * @param chapterVersion 사용자가 <b>실제로 소비한</b> 버전. 웹은 최신이지만 영상은 자산 버전이 뒤처져
 *     있을 수 있으므로, 무조건 최신으로 기록하면 구버전을 본 사람의 오답이 최신 버전의 실패로 잘못
 *     집계된다
 * @param blockId 신호가 붙는 지점. {@link SignalType#PROGRESS} 만 없어도 된다
 * @param format 임계치 판정에는 쓰지 않는다(형태별로 나누면 신호가 쪼개져 초기 규모에서 어느 쪽도
 *     임계치를 못 넘는다). 수정안의 근거로 형태별 분해를 만들 때만 쓴다
 * @param publishedAt null 이면 아직 집계 경로로 발행되지 않았다 — 이 테이블이 곧 집단 루프의 outbox다
 */
public record Signal(
        Long id,
        Long userId,
        Long chapterId,
        int chapterVersion,
        String blockId,
        DeliveryFormat format,
        SignalType type,
        Map<String, Object> payload,
        LocalDateTime occurredAt,
        LocalDateTime publishedAt) {

    public Signal {
        if (userId == null) {
            throw new IllegalArgumentException("신호에는 사용자가 필요합니다.");
        }
        if (chapterId == null) {
            throw new IllegalArgumentException("신호에는 챕터가 필요합니다.");
        }
        // 0 은 "본문이 아직 없음"이다. 본문 없는 챕터에서는 신호가 발생할 수 없으므로, 0 이 들어오는 것은
        // 소비한 버전을 놓친 것이고 그 신호는 어느 버전과도 비교할 수 없다.
        if (chapterVersion < 1) {
            throw new IllegalArgumentException("소비한 챕터 버전이 필요합니다: " + chapterVersion);
        }
        if (format == null) {
            throw new IllegalArgumentException("딜리버리 형태는 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("신호 종류는 필수입니다.");
        }
        if (type.isBlockRequired() && !BlockIds.isValid(blockId)) {
            throw new IllegalArgumentException(type + " 신호에는 블록 ID 가 필요합니다: " + blockId);
        }
        if (blockId != null && !BlockIds.isValid(blockId)) {
            throw new IllegalArgumentException("블록 ID 형식이 아닙니다: " + blockId);
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        for (String required : type.requiredPayloadKeys()) {
            if (payload.get(required) == null) {
                throw new IllegalArgumentException(type + " 신호에 '" + required + "' 가 필요합니다.");
            }
        }
        // 진행률은 숫자여야 한다. 집계가 이 값을 SQL 에서 숫자로 캐스팅하므로, 문자열이 한 줄이라도
        // 섞이면 그 사용자의 레이어 조회가 통째로 실패한다 — 저장 시점에 막는 편이 싸다.
        if (type == SignalType.PROGRESS) {
            requirePercent(payload.get("percent"));
        }
        occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
    }

    public static Signal of(
            Long userId,
            Long chapterId,
            int chapterVersion,
            String blockId,
            DeliveryFormat format,
            SignalType type,
            Map<String, Object> payload) {
        return new Signal(null, userId, chapterId, chapterVersion, blockId, format, type, payload, null, null);
    }

    private static void requirePercent(Object raw) {
        double percent;
        if (raw instanceof Number number) {
            percent = number.doubleValue();
        } else {
            try {
                percent = Double.parseDouble(String.valueOf(raw));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("진행률은 숫자여야 합니다: " + raw);
            }
        }
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("진행률은 0~100 이어야 합니다: " + percent);
        }
    }

    /** 개인화 보충 블록에 붙은 신호. 공용 본문에는 없는 블록이므로 집단 집계에서 구분해야 한다. */
    public boolean isOnSupplementBlock() {
        return BlockIds.isSupplement(blockId);
    }

    public Signal published(LocalDateTime at) {
        return new Signal(id, userId, chapterId, chapterVersion, blockId, format, type, payload, occurredAt, at);
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
