package com.mindplates.nextchapter.application.generation.port.out;

import java.util.List;

/**
 * 배치 조회 결과.
 *
 * @param ended 처리가 끝났는지. 끝나지 않았으면 {@code results} 는 비어 있다
 * @param results 끝났을 때의 항목별 결과
 */
public record LlmBatchPollResult(boolean ended, List<LlmBatchItemResult> results) {

    public static LlmBatchPollResult inProgress() {
        return new LlmBatchPollResult(false, List.of());
    }

    public static LlmBatchPollResult ended(List<LlmBatchItemResult> results) {
        return new LlmBatchPollResult(true, results);
    }
}
