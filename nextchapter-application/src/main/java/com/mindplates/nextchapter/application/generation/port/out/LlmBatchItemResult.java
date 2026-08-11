package com.mindplates.nextchapter.application.generation.port.out;

/**
 * 배치 항목 하나의 결과.
 *
 * <p><b>항목별로 성공·실패가 갈린다.</b> 배치가 끝났다는 것이 전 항목 성공을 뜻하지 않으므로, 성공 여부를
 * 항목마다 들고 다녀야 한다 — 뭉뚱그리면 실패한 챕터가 조용히 빠진 채 배치가 완료로 처리된다.
 *
 * @param customId 요청의 {@code customId} 그대로. 이것으로 어느 챕터인지 되짚는다
 * @param text 성공했을 때의 응답 본문. 실패면 null
 */
public record LlmBatchItemResult(
        String customId, boolean succeeded, String text, int inputTokens, int outputTokens, String errorMessage) {

    public static LlmBatchItemResult succeeded(String customId, String text, int inputTokens, int outputTokens) {
        return new LlmBatchItemResult(customId, true, text, inputTokens, outputTokens, null);
    }

    public static LlmBatchItemResult failed(String customId, String errorMessage) {
        return new LlmBatchItemResult(customId, false, null, 0, 0, errorMessage);
    }
}
