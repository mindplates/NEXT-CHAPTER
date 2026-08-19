package com.mindplates.nextchapter.application.admin.service;

import com.mindplates.nextchapter.application.admin.port.in.GetCollectiveThresholdSettingsUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateCollectiveThresholdSettingsUseCase;
import com.mindplates.nextchapter.application.admin.port.out.LoadCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.application.admin.view.CollectiveThresholdView;
import com.mindplates.nextchapter.domain.admin.model.CollectiveThresholdSettings;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 집단 루프 임계치 조회·변경. 관리 화면이 이 값을 조정한다. */
@Service
@RequiredArgsConstructor
public class CollectiveThresholdSettingsService
        implements GetCollectiveThresholdSettingsUseCase, UpdateCollectiveThresholdSettingsUseCase {

    private static final Logger log = LoggerFactory.getLogger(CollectiveThresholdSettingsService.class);

    private final LoadCollectiveThresholdSettingsPort loadCollectiveThresholdSettingsPort;
    private final SaveCollectiveThresholdSettingsPort saveCollectiveThresholdSettingsPort;

    @Override
    @Transactional(readOnly = true)
    public CollectiveThresholdView current() {
        return toView(loadCollectiveThresholdSettingsPort.load());
    }

    @Override
    @Transactional
    public CollectiveThresholdView update(int questionThreshold, int minAttempts, int wrongRatePercent, String actor) {
        CollectiveThresholdSettings saved = saveCollectiveThresholdSettingsPort.save(loadCollectiveThresholdSettingsPort
                .load()
                .withValues(questionThreshold, minAttempts, wrongRatePercent, actor));
        log.info(
                "[집단 루프] 임계치 변경 질문={} 최소시도={} 오답률={}% actor={}",
                saved.questionThreshold(), saved.minAttempts(), saved.wrongRatePercent(), actor);
        return toView(saved);
    }

    private static CollectiveThresholdView toView(CollectiveThresholdSettings settings) {
        return new CollectiveThresholdView(
                settings.questionThreshold(),
                settings.minAttempts(),
                settings.wrongRatePercent(),
                settings.updatedBy());
    }
}
