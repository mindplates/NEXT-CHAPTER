package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.generation.model.GenerationFailure;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import java.util.List;
import java.util.Optional;

public interface LoadGenerationFailurePort {

    Optional<GenerationFailure> findById(Long id);

    /** 상태가 null 이면 전체. 큐 화면의 기본 목록은 OPEN 이다. */
    List<GenerationFailure> findByStatus(GenerationFailureStatus status);

    List<GenerationFailure> findBySkeletonId(Long skeletonId);

    Optional<GenerationFailure> findByRecord(String topic, int partition, long offset);
}
