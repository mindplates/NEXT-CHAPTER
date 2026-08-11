package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GenerateChapterOutlinesUseCase;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ② 개요 생성 컨슈머.
 *
 * <p>완료 보고가 챕터 수만큼 본문 생성 메시지를 발행한다 — 여기가 병렬 구간이 열리는 지점이다.
 *
 * <p>실패 처리는 P2.8 이다. 예외를 그대로 올려 재시도에 맡긴다.
 */
@Component
public class ChapterOutlineListener {

    private static final Logger log = LoggerFactory.getLogger(ChapterOutlineListener.class);

    private final GenerateChapterOutlinesUseCase generateChapterOutlinesUseCase;
    private final AdvanceGenerationStageUseCase advanceGenerationStageUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChapterOutlineListener(
            GenerateChapterOutlinesUseCase generateChapterOutlinesUseCase,
            AdvanceGenerationStageUseCase advanceGenerationStageUseCase) {
        this.generateChapterOutlinesUseCase = generateChapterOutlinesUseCase;
        this.advanceGenerationStageUseCase = advanceGenerationStageUseCase;
    }

    @KafkaListener(topics = "${nextchapter.kafka.prefix:}" + GenerationTopicProperties.OUTLINE_REQUESTED)
    public void onOutlinesRequested(String payload) throws Exception {
        GenerationEvents.OutlinesRequested event =
                objectMapper.readValue(payload, GenerationEvents.OutlinesRequested.class);

        try {
            int outlines = generateChapterOutlinesUseCase.generate(event.skeletonId());
            log.info("[개요] skeletonId={} 챕터 {}개 개요 생성", event.skeletonId(), outlines);
            advanceGenerationStageUseCase.outlinesCompleted(event.skeletonId());
        } catch (BudgetExceededException exception) {
            BudgetStopReporter.report(advanceGenerationStageUseCase, event.skeletonId(), exception);
        }
    }
}
