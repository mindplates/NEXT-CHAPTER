package com.mindplates.nextchapter.domain.admin.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 호출 한 번의 사용량.
 *
 * @param skeletonId null 이면 뼈대에 귀속되지 않는 호출이다(주제 매핑·임베딩). 일일 상한에는 포함되고
 *     뼈대당 상한에는 포함되지 않는다 — 그 호출은 어느 뼈대의 책임도 아니다
 * @param usageDate KST 기준 날짜. "하루"의 경계가 일일 상한의 값을 바꾸므로 기준을 고정한다
 */
public record AiUsageRecord(
        Long id,
        Long skeletonId,
        AiStage stage,
        AiVendor vendor,
        String model,
        int inputTokens,
        int outputTokens,
        LocalDate usageDate,
        LocalDateTime createdAt) {

    public AiUsageRecord {
        if (stage == null || vendor == null) {
            throw new IllegalArgumentException("단계와 벤더는 필수입니다.");
        }
        model = Strings.requireText(model, "모델");
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("토큰 수는 0 이상이어야 합니다.");
        }
        if (usageDate == null) {
            throw new IllegalArgumentException("사용 날짜는 필수입니다.");
        }
    }

    public static AiUsageRecord of(
            Long skeletonId,
            AiStage stage,
            AiVendor vendor,
            String model,
            int inputTokens,
            int outputTokens,
            LocalDate usageDate) {
        return new AiUsageRecord(null, skeletonId, stage, vendor, model, inputTokens, outputTokens, usageDate, null);
    }

    public long totalTokens() {
        return (long) inputTokens + outputTokens;
    }
}
