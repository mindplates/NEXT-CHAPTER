package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.generation.model.GenerationStage;

/**
 * 운영자 재시도 — 원본 페이로드를 그 단계의 토픽에 <b>그대로</b> 다시 발행한다.
 *
 * <p>{@link GenerationEventPublisherPort} 와 나눈 이유는 이쪽이 페이로드를 만들지 않기 때문이다. 저장된
 * 페이로드를 그대로 보내야 실패한 그 항목이 재현되고, 다시 만들면 그 사이 바뀐 데이터가 섞여 <b>다른
 * 요청</b>이 된다 — 원래 실패의 재시도가 아니게 된다.
 */
public interface GenerationRetryPublisherPort {

    void republish(GenerationStage stage, String payload, Long skeletonId, Long chapterId);
}
