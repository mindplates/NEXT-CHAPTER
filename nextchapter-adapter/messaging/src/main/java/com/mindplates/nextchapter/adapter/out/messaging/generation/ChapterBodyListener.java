package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GenerateChapterBodyUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ③ 본문 생성 컨슈머 — 챕터 하나가 메시지 하나다.
 *
 * <p><b>실패가 챕터 단위로 격리된다.</b> 메시지 키가 챕터 ID 이므로 실패한 챕터의 메시지만 재시도되고,
 * 같은 뼈대의 다른 챕터는 계속 진행된다. 뼈대 단위로 묶었다면 챕터 하나의 실패가 전부를 되돌린다.
 *
 * <p>완료 보고는 <b>건너뛴 경우에도</b> 한다. 중복 메시지로 본문 생성을 건너뛰었더라도 그 챕터는 끝난
 * 상태이고, 보고하지 않으면 마지막 챕터가 중복 메시지였을 때 파이프라인이 영원히 대기한다.
 */
@Component
public class ChapterBodyListener {

    private static final Logger log = LoggerFactory.getLogger(ChapterBodyListener.class);

    private final GenerateChapterBodyUseCase generateChapterBodyUseCase;
    private final AdvanceGenerationStageUseCase advanceGenerationStageUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChapterBodyListener(
            GenerateChapterBodyUseCase generateChapterBodyUseCase,
            AdvanceGenerationStageUseCase advanceGenerationStageUseCase) {
        this.generateChapterBodyUseCase = generateChapterBodyUseCase;
        this.advanceGenerationStageUseCase = advanceGenerationStageUseCase;
    }

    @KafkaListener(
            topics = "${nextchapter.kafka.prefix:}" + GenerationTopicProperties.BODY_REQUESTED,
            concurrency = "${nextchapter.kafka.body-concurrency:4}")
    public void onBodyRequested(String payload) throws Exception {
        GenerationEvents.BodyRequested event = objectMapper.readValue(payload, GenerationEvents.BodyRequested.class);

        boolean created = generateChapterBodyUseCase.generate(event.skeletonId(), event.chapterId());
        log.info("[본문] chapterKey={} {}", event.chapterKey(), created ? "생성" : "건너뜀(이미 있음)");
        advanceGenerationStageUseCase.chapterBodyCompleted(event.skeletonId());
    }
}
