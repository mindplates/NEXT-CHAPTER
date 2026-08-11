package com.mindplates.nextchapter.application.generation.port.out;

import java.util.List;

/**
 * 임베딩 결과.
 *
 * <p>{@code model} 과 {@code dimension} 을 결과에 담는 이유는 벡터를 저장할 때 어느 모델로 만든
 * 것인지를 함께 남겨야 하기 때문이다. 모델이 다른 벡터 간 코사인 유사도는 비교 자체가 무의미한데
 * <b>에러가 나지 않는다</b> — 모델을 기록해 두지 않으면 재임베딩이 끝났는지도 알 수 없다.
 */
public record EmbeddingResult(String model, int dimension, List<float[]> vectors, int inputTokens) {

    public float[] first() {
        if (vectors.isEmpty()) {
            throw new IllegalStateException("임베딩 결과가 비어 있습니다.");
        }
        return vectors.get(0);
    }
}
