package com.mindplates.nextchapter.application.admin.view;

import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.time.LocalDateTime;

/**
 * 단계 설정 + 실제로 쓸 수 있는 상태인지.
 *
 * <p>{@code credentialRegistered} 를 함께 내리는 이유는 설정만 맞춰 두고 키를 넣지 않은 상태가
 * 흔하기 때문이다. 그 상태는 화면에서 정상으로 보이고 생성 잡에서만 실패한다.
 */
public record AiStageSettingView(
        AiStage stage,
        AiVendor vendor,
        String model,
        boolean vendorSupported,
        boolean credentialRegistered,
        String updatedBy,
        LocalDateTime updatedAt) {

    public boolean isReady() {
        return vendorSupported && credentialRegistered;
    }
}
