package com.mindplates.nextchapter.application.generation.port.out;

/**
 * 완성 결과.
 *
 * <p>토큰 수를 함께 돌려받는 것이 비용 상한(P2.7)의 전제다. 호출한 뒤에 사용량을 알 수 없으면
 * 뼈대당·일일 상한을 계산할 방법이 없고, 상한 없는 사전 생성은 예산을 통째로 태울 수 있다.
 */
public record LlmCompletionResult(String text, int inputTokens, int outputTokens) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
