package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GenerateSkeletonGraphUseCase;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ① 그래프 생성 컨슈머 — 토픽 체인의 첫 칸.
 *
 * <p>생성과 단계 전환을 나눠 부른다. 전환({@link AdvanceGenerationStageUseCase#graphCompleted})이
 * 다음 이벤트 발행까지 함께 하므로, 여기서 성공을 보고하지 않으면 파이프라인이 이 지점에서 멈춘다.
 *
 * <p><b>실패 처리는 여기서 하지 않는다.</b> 예외를 그대로 올려 Spring Kafka 의 재시도에 맡긴다 —
 * 즉시 실패로 기록하면 일시적 오류(벤더 5xx, 타임아웃)에도 뼈대가 죽는다. 재시도 횟수와 운영자 큐
 * 이관은 P2.8 에서 정책으로 붙인다.
 */
@Component
public class SkeletonGraphListener {

    private static final Logger log = LoggerFactory.getLogger(SkeletonGraphListener.class);

    private final GenerateSkeletonGraphUseCase generateSkeletonGraphUseCase;
    private final AdvanceGenerationStageUseCase advanceGenerationStageUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkeletonGraphListener(
            GenerateSkeletonGraphUseCase generateSkeletonGraphUseCase,
            AdvanceGenerationStageUseCase advanceGenerationStageUseCase) {
        this.generateSkeletonGraphUseCase = generateSkeletonGraphUseCase;
        this.advanceGenerationStageUseCase = advanceGenerationStageUseCase;
    }

    @KafkaListener(topics = "${nextchapter.kafka.prefix:}" + GenerationTopicProperties.GRAPH_REQUESTED)
    public void onGraphRequested(String payload) throws Exception {
        GenerationEvents.GraphRequested event = objectMapper.readValue(payload, GenerationEvents.GraphRequested.class);

        try {
            int chapters = generateSkeletonGraphUseCase.generate(event.skeletonId());
            log.info("[그래프] skeletonId={} 챕터 {}개 생성", event.skeletonId(), chapters);
            advanceGenerationStageUseCase.graphCompleted(event.skeletonId());
        } catch (BudgetExceededException exception) {
            BudgetStopReporter.report(advanceGenerationStageUseCase, event.skeletonId(), exception);
        }
    }
}
