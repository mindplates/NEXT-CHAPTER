package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.application.admin.port.out.LoadAiBudgetSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiBudgetSettingsPort;
import com.mindplates.nextchapter.domain.admin.model.AiBudgetSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AiBudgetSettingsPersistenceAdapter implements LoadAiBudgetSettingsPort, SaveAiBudgetSettingsPort {

    private final AiBudgetSettingsJpaRepository budgetSettingsRepository;

    /**
     * 행이 없으면 예외다. 무제한으로 기본값을 채우지 않는 이유는 그게 <b>안전장치가 조용히 꺼지는</b> 경로이기
     * 때문이다 — 마이그레이션이 안 돌았거나 누가 행을 지운 상태에서 생성이 무제한으로 돌아버린다.
     */
    @Override
    @Transactional(readOnly = true)
    public AiBudgetSettings load() {
        return budgetSettingsRepository
                .findById(AiBudgetSettingsJpaEntity.SINGLETON_ID)
                .map(AiBudgetSettingsPersistenceAdapter::toDomain)
                .orElseThrow(() -> new IllegalStateException("예산 설정 행이 없습니다. 마이그레이션 상태를 확인해야 합니다."));
    }

    /**
     * 새 엔티티를 만들어 {@code save} 하지 않고 <b>관리 상태의 행을 찾아 고친다</b>. 단일 행 테이블에
     * {@code merge} 를 쓰면 준영속 엔티티의 null 필드가 그대로 복사되어 {@code created_at} 이 사라진 것처럼
     * 보이고(컬럼은 {@code updatable = false} 라 DB 에는 남지만 반환 객체에는 없다), 반환값을 그대로 화면에
     * 내리는 경로에서 조용히 틀린 값이 된다.
     */
    @Override
    @Transactional
    public AiBudgetSettings save(AiBudgetSettings settings) {
        AiBudgetSettingsJpaEntity entity = budgetSettingsRepository
                .findById(AiBudgetSettingsJpaEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("예산 설정 행이 없습니다. 마이그레이션 상태를 확인해야 합니다."));
        entity.setPerSkeletonTokenLimit(settings.perSkeletonTokenLimit());
        entity.setDailyTokenLimit(settings.dailyTokenLimit());
        entity.setUpdatedBy(settings.updatedBy());
        return toDomain(budgetSettingsRepository.save(entity));
    }

    private static AiBudgetSettings toDomain(AiBudgetSettingsJpaEntity entity) {
        return new AiBudgetSettings(
                entity.getPerSkeletonTokenLimit(),
                entity.getDailyTokenLimit(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
