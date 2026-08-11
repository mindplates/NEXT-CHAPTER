package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.application.admin.port.out.LoadAiStageSettingPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiStageSettingPort;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiStageSetting;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단계 설정과 자격 증명을 한 어댑터에 합칠 수 없다 — 두 Load 포트의 {@code findAll()} 이 시그니처는
 * 같고 반환 타입만 달라서 한 클래스가 동시에 구현할 수 없다. 카탈로그 어댑터를 계층별로 나눈 것과
 * 같은 제약이다.
 */
@Component
@RequiredArgsConstructor
public class AiStageSettingPersistenceAdapter implements LoadAiStageSettingPort, SaveAiStageSettingPort {

    private final AiStageSettingJpaRepository stageSettingRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AiStageSetting> findAll() {
        return stageSettingRepository.findAllByOrderByStageAsc().stream()
                .map(AiStageSettingPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiStageSetting> findByStage(AiStage stage) {
        return stageSettingRepository.findByStage(stage).map(AiStageSettingPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public AiStageSetting save(AiStageSetting setting) {
        return toDomain(stageSettingRepository.save(AiStageSettingJpaEntity.builder()
                .id(setting.id())
                .stage(setting.stage())
                .vendor(setting.vendor())
                .model(setting.model())
                .updatedBy(setting.updatedBy())
                .build()));
    }

    private static AiStageSetting toDomain(AiStageSettingJpaEntity entity) {
        return new AiStageSetting(
                entity.getId(),
                entity.getStage(),
                entity.getVendor(),
                entity.getModel(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
