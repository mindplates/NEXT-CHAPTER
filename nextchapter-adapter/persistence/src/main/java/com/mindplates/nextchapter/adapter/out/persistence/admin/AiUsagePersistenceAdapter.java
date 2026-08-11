package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.application.admin.port.out.LoadAiUsagePort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiUsagePort;
import com.mindplates.nextchapter.domain.admin.model.AiUsageRecord;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AiUsagePersistenceAdapter implements LoadAiUsagePort, SaveAiUsagePort {

    private final AiUsageRecordJpaRepository usageRepository;

    @Override
    @Transactional(readOnly = true)
    public long totalTokensBySkeletonId(Long skeletonId) {
        return skeletonId == null ? 0 : usageRepository.sumTokensBySkeletonId(skeletonId);
    }

    @Override
    @Transactional(readOnly = true)
    public long totalTokensOn(LocalDate usageDate) {
        return usageRepository.sumTokensOn(usageDate);
    }

    @Override
    @Transactional
    public AiUsageRecord record(AiUsageRecord usage) {
        AiUsageRecordJpaEntity saved = usageRepository.save(AiUsageRecordJpaEntity.builder()
                .skeletonId(usage.skeletonId())
                .stage(usage.stage())
                .vendor(usage.vendor())
                .model(usage.model())
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .usageDate(usage.usageDate())
                .build());
        return new AiUsageRecord(
                saved.getId(),
                saved.getSkeletonId(),
                saved.getStage(),
                saved.getVendor(),
                saved.getModel(),
                saved.getInputTokens(),
                saved.getOutputTokens(),
                saved.getUsageDate(),
                saved.getCreatedAt());
    }
}
