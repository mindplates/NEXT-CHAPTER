package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.application.admin.port.out.LoadCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.domain.admin.model.CollectiveThresholdSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CollectiveThresholdSettingsPersistenceAdapter
        implements LoadCollectiveThresholdSettingsPort, SaveCollectiveThresholdSettingsPort {

    private final CollectiveThresholdSettingsJpaRepository thresholdSettingsRepository;

    /**
     * 행이 없으면 예외다. 기본값으로 채우지 않는 이유는 그게 <b>안전장치가 조용히 꺼지는</b> 경로이기
     * 때문이다 — 마이그레이션이 안 돌았거나 누가 행을 지운 상태에서 임계치 없이 동작하면 트리거가
     * 영원히 발생하지 않는다.
     */
    @Override
    @Transactional(readOnly = true)
    public CollectiveThresholdSettings load() {
        return thresholdSettingsRepository
                .findById(CollectiveThresholdSettingsJpaEntity.SINGLETON_ID)
                .map(CollectiveThresholdSettingsPersistenceAdapter::toDomain)
                .orElseThrow(() -> new IllegalStateException("집단 루프 임계치 설정 행이 없습니다. 마이그레이션 상태를 확인해야 합니다."));
    }

    /**
     * 새 엔티티를 만들어 저장하지 않고 <b>관리 상태의 행을 찾아 고친다</b>. 단일 행 테이블에 {@code merge} 를
     * 쓰면 준영속 엔티티의 null 필드가 그대로 복사되어 {@code created_at} 이 사라진 것처럼 보인다.
     */
    @Override
    @Transactional
    public CollectiveThresholdSettings save(CollectiveThresholdSettings settings) {
        CollectiveThresholdSettingsJpaEntity entity = thresholdSettingsRepository
                .findById(CollectiveThresholdSettingsJpaEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("집단 루프 임계치 설정 행이 없습니다. 마이그레이션 상태를 확인해야 합니다."));
        entity.setQuestionThreshold(settings.questionThreshold());
        entity.setMinAttempts(settings.minAttempts());
        entity.setWrongRatePercent(settings.wrongRatePercent());
        entity.setUpdatedBy(settings.updatedBy());
        return toDomain(thresholdSettingsRepository.save(entity));
    }

    private static CollectiveThresholdSettings toDomain(CollectiveThresholdSettingsJpaEntity entity) {
        return new CollectiveThresholdSettings(
                entity.getQuestionThreshold(),
                entity.getMinAttempts(),
                entity.getWrongRatePercent(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
