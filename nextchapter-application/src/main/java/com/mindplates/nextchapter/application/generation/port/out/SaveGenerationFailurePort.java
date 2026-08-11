package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.generation.model.GenerationFailure;

public interface SaveGenerationFailurePort {

    /**
     * 같은 메시지 좌표의 항목이 이미 있으면 그것을 돌려준다.
     *
     * <p>같은 메시지가 두 번 소진될 수 있다 — 파티션 재할당, 오프셋 되돌림, 컨슈머 재기동. 그때 큐에
     * 같은 항목이 여러 줄 쌓이면 운영자가 몇 개가 실제 문제인지 알 수 없다.
     */
    GenerationFailure save(GenerationFailure failure);
}
