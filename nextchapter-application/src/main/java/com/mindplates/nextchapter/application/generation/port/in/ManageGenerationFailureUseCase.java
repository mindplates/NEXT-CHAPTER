package com.mindplates.nextchapter.application.generation.port.in;

import com.mindplates.nextchapter.application.generation.view.GenerationFailureView;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import java.util.List;

/** 운영자 큐 — 조회 · 재시도 · 닫기. */
public interface ManageGenerationFailureUseCase {

    List<GenerationFailureView> list(GenerationFailureStatus status);

    List<GenerationFailureView> bySkeletonId(Long skeletonId);

    /**
     * 저장된 페이로드를 그 단계의 토픽에 다시 발행한다.
     *
     * <p>뼈대 상태를 그 단계로 되돌린다 — 실패로 덮여 있으면 컨슈머의 단계 전환이 거절된다.
     */
    GenerationFailureView retry(Long id, String actor);

    GenerationFailureView resolve(Long id, String actor);
}
