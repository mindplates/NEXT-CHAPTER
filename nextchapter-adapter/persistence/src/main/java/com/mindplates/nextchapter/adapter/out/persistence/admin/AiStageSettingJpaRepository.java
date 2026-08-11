package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.domain.admin.model.AiStage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiStageSettingJpaRepository extends JpaRepository<AiStageSettingJpaEntity, Long> {

    List<AiStageSettingJpaEntity> findAllByOrderByStageAsc();

    Optional<AiStageSettingJpaEntity> findByStage(AiStage stage);
}
