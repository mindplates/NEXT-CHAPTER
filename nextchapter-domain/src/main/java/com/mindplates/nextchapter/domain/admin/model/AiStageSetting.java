package com.mindplates.nextchapter.domain.admin.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 단계 하나에 대한 벤더·모델 설정. 단계당 정확히 하나다 (DB UNIQUE).
 *
 * <p>{@code model} 이 문자열인 이유는 벤더가 모델을 수시로 추가하기 때문이다. enum 으로 두면 모델이
 * 하나 나올 때마다 배포가 필요해지고, 그건 "관리 화면에서 지정한다"는 결정을 무력화한다.
 */
public record AiStageSetting(
        Long id,
        AiStage stage,
        AiVendor vendor,
        String model,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public AiStageSetting {
        if (stage == null) {
            throw new IllegalArgumentException("단계는 필수입니다.");
        }
        if (vendor == null) {
            throw new IllegalArgumentException("벤더는 필수입니다.");
        }
        model = Strings.requireMaxLength(Strings.requireText(model, "모델"), 120, "모델");
        updatedBy = Strings.trimToNull(updatedBy);
    }

    public AiStageSetting withSelection(AiVendor newVendor, String newModel, String actor) {
        return new AiStageSetting(id, stage, newVendor, newModel, actor, createdAt, updatedAt);
    }
}
