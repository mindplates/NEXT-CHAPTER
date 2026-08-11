package com.mindplates.nextchapter.adapter.out.messaging.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mindplates.nextchapter.application.generation.port.in.RecordGenerationFailureUseCase;
import com.mindplates.nextchapter.application.generation.port.in.RecordGenerationFailureUseCase.GenerationFailureCommand;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

/**
 * 복구기는 재시도를 소진한 메시지의 <b>마지막 관문</b>이다. 여기서 기록하지 못하면 그 실패는 어디에도
 * 남지 않는다 — 메시지는 사라지고 뼈대는 생성 중 상태로 남는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("생성 실패 복구기")
class GenerationFailureRecovererTest {

    @Mock
    RecordGenerationFailureUseCase recordGenerationFailureUseCase;

    private GenerationFailureRecoverer recoverer() {
        return new GenerationFailureRecoverer(
                recordGenerationFailureUseCase,
                new GenerationRetryProperties(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10)));
    }

    private static ConsumerRecord<String, String> record(String topic, String payload) {
        return new ConsumerRecord<>(topic, 3, 420L, "key", payload);
    }

    @Test
    @DisplayName("본문 실패는 뼈대·챕터·좌표를 담아 큐로 간다")
    void recordsBodyFailure() {
        recoverer()
                .accept(
                        record(
                                GenerationTopicProperties.BODY_REQUESTED,
                                "{\"skeletonId\":5,\"chapterId\":101,\"chapterKey\":\"loss\"}"),
                        new ExternalApiException("벤더 오류"));

        ArgumentCaptor<GenerationFailureCommand> captor = ArgumentCaptor.forClass(GenerationFailureCommand.class);
        verify(recordGenerationFailureUseCase).record(captor.capture());
        GenerationFailureCommand command = captor.getValue();
        assertThat(command.skeletonId()).isEqualTo(5L);
        assertThat(command.chapterId()).isEqualTo(101L);
        assertThat(command.stage()).isEqualTo(GenerationStage.BODY);
        assertThat(command.partition()).isEqualTo(3);
        assertThat(command.offset()).isEqualTo(420L);
        assertThat(command.attempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("토픽 접두어가 붙어 있어도 단계를 알아낸다")
    void resolvesStageWithPrefix() {
        recoverer()
                .accept(
                        record("staging." + GenerationTopicProperties.GRAPH_REQUESTED, "{\"skeletonId\":5}"),
                        new ExternalApiException("벤더 오류"));

        ArgumentCaptor<GenerationFailureCommand> captor = ArgumentCaptor.forClass(GenerationFailureCommand.class);
        verify(recordGenerationFailureUseCase).record(captor.capture());
        assertThat(captor.getValue().stage()).isEqualTo(GenerationStage.GRAPH);
        assertThat(captor.getValue().chapterId()).isNull();
    }

    /**
     * 감싼 예외를 그대로 기록하면 큐의 모든 줄이 {@code ListenerExecutionFailedException} 으로 보이고,
     * 그러면 무엇이 실패했는지 구분할 수 없다.
     */
    @Test
    @DisplayName("감싼 예외의 실제 원인을 기록한다")
    void unwrapsRootCause() {
        recoverer()
                .accept(
                        record(GenerationTopicProperties.BODY_REQUESTED, "{\"skeletonId\":5,\"chapterId\":101}"),
                        new ListenerExecutionFailedException("리스너 실패", new ExternalApiException("벤더 500")));

        ArgumentCaptor<GenerationFailureCommand> captor = ArgumentCaptor.forClass(GenerationFailureCommand.class);
        verify(recordGenerationFailureUseCase).record(captor.capture());
        assertThat(captor.getValue().errorType()).isEqualTo(ExternalApiException.class.getName());
        assertThat(captor.getValue().errorMessage()).isEqualTo("벤더 500");
    }

    /** 읽을 수 없는 메시지가 조용히 사라지는 것이 이 큐를 만든 이유 그 자체다. */
    @Test
    @DisplayName("페이로드를 읽지 못해도 큐에 올린다")
    void recordsUnparseablePayload() {
        recoverer().accept(record(GenerationTopicProperties.BODY_REQUESTED, "not-json"), new RuntimeException("깨짐"));

        ArgumentCaptor<GenerationFailureCommand> captor = ArgumentCaptor.forClass(GenerationFailureCommand.class);
        verify(recordGenerationFailureUseCase).record(captor.capture());
        assertThat(captor.getValue().skeletonId()).isNull();
        assertThat(captor.getValue().payload()).isEqualTo("not-json");
    }

    /** 이 핸들러는 컨테이너 전체에 적용된다 — outbox 컨슈머의 실패는 생성 큐의 대상이 아니다. */
    @Test
    @DisplayName("생성 토픽이 아닌 메시지는 큐에 올리지 않는다")
    void ignoresNonGenerationTopic() {
        recoverer().accept(record("chapter.outbox.events", "{}"), new RuntimeException("무관"));

        verifyNoInteractions(recordGenerationFailureUseCase);
    }

    /**
     * 여기서 예외를 던지면 Spring Kafka 가 오프셋을 커밋하지 않아 <b>같은 메시지를 영원히 다시 처리</b>하고
     * 그 파티션이 막힌다. 큐 한 줄을 잃는 것보다 훨씬 나쁘다.
     */
    @Test
    @DisplayName("큐 기록이 실패해도 예외를 올리지 않는다 — 파티션이 막힌다")
    void swallowsRecordingFailure() {
        doThrow(new RuntimeException("DB 다운"))
                .when(recordGenerationFailureUseCase)
                .record(any());

        assertThatCode(() -> recoverer()
                        .accept(
                                record(GenerationTopicProperties.BODY_REQUESTED, "{\"skeletonId\":5}"),
                                new ExternalApiException("벤더 오류")))
                .doesNotThrowAnyException();
    }
}
