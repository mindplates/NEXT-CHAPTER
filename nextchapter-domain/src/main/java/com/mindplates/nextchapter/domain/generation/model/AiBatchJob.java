package com.mindplates.nextchapter.domain.generation.model;

import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.time.LocalDateTime;

/**
 * 제출한 배치 하나.
 *
 * <p><b>벤더와 모델을 함께 저장하는 것이 이 레코드의 핵심이다.</b> 배치는 최대 24시간 살아 있고 그 사이
 * 관리자가 단계 설정을 바꿀 수 있다. 조회할 때 현재 설정을 다시 읽으면 다른 벤더의 API 로 다른 벤더의
 * 배치 ID 를 묻게 되고, 그건 "배치를 찾을 수 없음"으로 나타난다 — 제출한 배치가 조용히 유실된다.
 *
 * @param batchId 벤더가 준 ID. 유일하다 — 같은 배치를 두 번 반영하면 챕터마다 v2 가 쌓인다
 * @param itemCount 제출한 항목 수. 결과 수와 다르면 빠진 챕터가 있다는 뜻이다
 */
public record AiBatchJob(
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public AiBatchJob {
        if (skeletonId == null) {
            throw new IllegalArgumentException("배치 잡은 뼈대에 속해야 합니다.");
        }
        if (stage == null || vendor == null || status == null) {
            throw new IllegalArgumentException("단계·벤더·상태는 필수입니다.");
        }
        model = Strings.requireText(model, "모델");
        batchId = Strings.requireText(batchId, "배치 ID");
        if (itemCount < 1) {
            throw new IllegalArgumentException("배치에는 항목이 하나 이상 있어야 합니다.");
        }
        failureReason = Strings.trimToNull(failureReason);
    }

    public static AiBatchJob submitted(
            Long skeletonId, GenerationStage stage, AiVendor vendor, String model, String batchId, int itemCount) {
        return new AiBatchJob(
                null,
                skeletonId,
                stage,
                vendor,
                model,
                batchId,
                AiBatchStatus.SUBMITTED,
                itemCount,
                null,
                null,
                null,
                null);
    }

    public AiBatchJob completed(LocalDateTime at) {
        return withStatus(AiBatchStatus.COMPLETED, null, at);
    }

    public AiBatchJob failed(String reason, LocalDateTime at) {
        return withStatus(AiBatchStatus.FAILED, reason, at);
    }

    private AiBatchJob withStatus(AiBatchStatus next, String reason, LocalDateTime at) {
        if (status.isTerminal()) {
            throw new IllegalStateException("이미 끝난 배치입니다: " + status);
        }
        return new AiBatchJob(
                id, skeletonId, stage, vendor, model, batchId, next, itemCount, reason, at, createdAt, updatedAt);
    }
}
