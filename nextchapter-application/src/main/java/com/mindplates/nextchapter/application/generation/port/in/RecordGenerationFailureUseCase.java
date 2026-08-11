package com.mindplates.nextchapter.application.generation.port.in;

import com.mindplates.nextchapter.domain.generation.model.GenerationStage;

/**
 * 재시도를 소진한 항목을 운영자 큐에 올리고 뼈대를 실패로 표시한다.
 *
 * <p>둘을 한 경로에 묶는 이유는 하나만 일어나면 조용히 틀리기 때문이다. 큐에만 올리면 뼈대는 생성 중
 * 상태로 남아 진행률이 거짓이 되고, 뼈대만 실패로 표시하면 무엇을 다시 돌려야 하는지 알 수 없다.
 */
public interface RecordGenerationFailureUseCase {

    void record(GenerationFailureCommand command);

    /**
     * @param chapterId 본문 단계만 채워진다
     * @param attempts 소진한 시도 횟수
     */
    record GenerationFailureCommand(
            Long skeletonId,
            Long chapterId,
            GenerationStage stage,
            String topic,
            int partition,
            long offset,
            String payload,
            String errorType,
            String errorMessage,
            int attempts) {}
}
