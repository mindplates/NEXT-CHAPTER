package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiBudgetSettings;

public interface LoadAiBudgetSettingsPort {

    /**
     * 예산 설정. 단일 행이라 없을 수 없다 — 마이그레이션이 심는다.
     *
     * <p>{@link java.util.Optional} 로 두지 않는 이유는 "설정이 없다"가 정상 상태가 아니기 때문이다.
     * 없으면 상한이 꺼진 것과 구분되지 않고, 그건 안전장치가 조용히 사라지는 것이다.
     */
    AiBudgetSettings load();
}
