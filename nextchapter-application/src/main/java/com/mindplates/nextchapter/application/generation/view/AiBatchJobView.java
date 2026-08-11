package com.mindplates.nextchapter.application.generation.view;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import com.mindplates.nextchapter.domain.generation.model.AiBatchJob;
import com.mindplates.nextchapter.domain.generation.model.AiBatchStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import java.time.LocalDateTime;

/**
 * 배치 잡 한 줄.
 *
 * <p>운영자에게 이 화면이 필요한 이유는 배치가 <b>최대 24시간 아무 진행률 변화 없이</b> 살아 있기 때문이다.
 * 진행률만 보면 멈춘 것과 배치를 기다리는 것이 구분되지 않는다.
 */
public record AiBatchJobView(
        Long id,
        Long skeletonId,
        GenerationStage stage,
        AiVendor vendor,
        String model,
        String batchId,
        AiBatchStatus status,
        int itemCount,
        String failureReason,
        LocalDateTime completedAt,
        LocalDateTime createdAt) {

    public static AiBatchJobView from(AiBatchJob job) {
        return new AiBatchJobView(
                job.id(),
                job.skeletonId(),
                job.stage(),
                job.vendor(),
                job.model(),
                job.batchId(),
                job.status(),
                job.itemCount(),
                job.failureReason(),
                job.completedAt(),
                job.createdAt());
    }
}
