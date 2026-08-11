package com.mindplates.nextchapter.application.generation.view;

import com.mindplates.nextchapter.domain.generation.model.GenerationFailure;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import java.time.LocalDateTime;

/**
 * 운영자 큐 한 줄.
 *
 * <p>페이로드를 내리지 않는다. 큐 화면이 필요한 것은 "무엇이 어디서 왜 실패했나"이고, 페이로드는 재시도
 * 경로가 서버 안에서 쓴다. 프롬프트와 챕터 내용이 섞여 있어 목록 응답에 실을 값이 아니다.
 */
public record GenerationFailureView(
        Long id,
        Long skeletonId,
        Long chapterId,
        GenerationStage stage,
        String errorType,
        String errorMessage,
        int attempts,
        GenerationFailureStatus status,
        String resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt) {

    public static GenerationFailureView from(GenerationFailure failure) {
        return new GenerationFailureView(
                failure.id(),
                failure.skeletonId(),
                failure.chapterId(),
                failure.stage(),
                failure.errorType(),
                failure.errorMessage(),
                failure.attempts(),
                failure.status(),
                failure.resolvedBy(),
                failure.resolvedAt(),
                failure.createdAt());
    }
}
