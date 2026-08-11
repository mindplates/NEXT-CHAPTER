package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.chapter.port.in.RecordChapterBodyUseCase;
import com.mindplates.nextchapter.application.chapter.port.in.command.RecordChapterBodyCommand;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterOutlinePort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterRelationPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("③ 본문 생성")
class ChapterBodyServiceTest {

    private static final Long SKELETON_ID = 5L;
    private static final Long CHAPTER_ID = 101L;

    private static final String VALID_RESPONSE =
            """
            {
              "blocks": [
                {"type": "heading", "text": "경사하강법이란"},
                {"type": "paragraph", "text": "손실을 줄이는 방향으로 파라미터를 옮긴다",
                 "attributes": {"sources": [{"title": "Deep Learning", "quote": "gradient descent updates"}]}},
                {"type": "formula", "text": "\\\\theta := \\\\theta - \\\\alpha \\\\nabla J"},
                {"type": "narration", "text": "이제 학습률을 봅시다"},
                {"type": "quiz", "attributes": {"question": "학습률이 크면?",
                 "choices": ["발산한다", "수렴한다"], "answer": 0}}
              ]
            }
            """;

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadCatalogTopicPort loadCatalogTopicPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterOutlinePort loadChapterOutlinePort;

    @Mock
    LoadChapterRelationPort loadChapterRelationPort;

    @Mock
    RecordChapterBodyUseCase recordChapterBodyUseCase;

    @Mock
    AiGateway aiGateway;

    @InjectMocks
    ChapterBodyService service;

    @BeforeEach
    void setUp() {
        when(loadSkeletonPort.findById(SKELETON_ID)).thenReturn(Optional.of(Skeleton.start(42L)));
        when(loadCatalogTopicPort.findById(42L))
                .thenReturn(Optional.of(new CatalogTopic(42L, 20L, "machine-learning", "머신러닝", null, 0, null, null)));
        when(loadChapterPort.findById(CHAPTER_ID)).thenReturn(Optional.of(chapter(CHAPTER_ID, "gradient-descent", 0)));
        when(loadChapterPort.findBySkeletonId(SKELETON_ID))
                .thenReturn(List.of(
                        chapter(100L, "loss-function", 0),
                        chapter(CHAPTER_ID, "gradient-descent", 0),
                        chapter(102L, "learning-rate", 0)));
        when(loadChapterOutlinePort.findByChapterId(CHAPTER_ID))
                .thenReturn(Optional.of(ChapterOutline.of(CHAPTER_ID, List.of("경사의 의미", "갱신 규칙"))));
        when(loadChapterRelationPort.findBySkeletonId(SKELETON_ID))
                .thenReturn(List.of(new ChapterRelation(
                        1L, SKELETON_ID, 100L, CHAPTER_ID, ChapterRelationType.PREREQUISITE, null)));
    }

    @Test
    @DisplayName("블록 문서를 기록 경로로 넘긴다 — ID 승계와 버전 규칙이 한 곳에만 있어야 한다")
    void recordsThroughVersioningUseCase() {
        stubResponse(VALID_RESPONSE);

        assertThat(service.generate(SKELETON_ID, CHAPTER_ID)).isTrue();

        ArgumentCaptor<RecordChapterBodyCommand> command = ArgumentCaptor.forClass(RecordChapterBodyCommand.class);
        verify(recordChapterBodyUseCase).record(org.mockito.ArgumentMatchers.eq(CHAPTER_ID), command.capture());
        assertThat(command.getValue().source()).isEqualTo(ChapterVersionSource.GENERATED);
        assertThat(command.getValue().blocks())
                .extracting(ProposedBlock::type)
                .containsExactly(
                        BlockType.HEADING, BlockType.PARAGRAPH, BlockType.FORMULA, BlockType.NARRATION, BlockType.QUIZ);
    }

    /** Kafka 는 at-least-once 다. 그냥 생성하면 "왜 바뀌었는지 설명할 수 없는 버전"이 이력에 쌓인다. */
    @Test
    @DisplayName("이미 본문이 있으면 건너뛴다 — 중복 메시지가 v2 를 만들지 않는다")
    void skipsWhenBodyExists() {
        when(loadChapterPort.findById(CHAPTER_ID)).thenReturn(Optional.of(chapter(CHAPTER_ID, "gradient-descent", 1)));

        assertThat(service.generate(SKELETON_ID, CHAPTER_ID)).isFalse();

        verify(recordChapterBodyUseCase, never()).record(any(), any());
        verify(aiGateway, never()).complete(any(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("개요가 없으면 본문을 만들지 않는다")
    void requiresOutline() {
        when(loadChapterOutlinePort.findByChapterId(CHAPTER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(SKELETON_ID, CHAPTER_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("개요가 없어");
        verify(aiGateway, never()).complete(any(), any(), anyString(), anyString(), anyInt());
    }

    /** 출처가 없으면 검증 패스가 검사할 대상이 없고, 그 상태는 "검증 통과"와 구분되지 않는다. */
    @Test
    @DisplayName("출처가 하나도 없으면 실패로 다룬다")
    void requiresAtLeastOneSource() {
        stubResponse(
                """
                {"blocks": [
                  {"type": "heading", "text": "제목"},
                  {"type": "paragraph", "text": "출처 없는 본문"}
                ]}
                """);

        assertThatThrownBy(() -> service.generate(SKELETON_ID, CHAPTER_ID))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("출처");
        verify(recordChapterBodyUseCase, never()).record(any(), any());
    }

    @Test
    @DisplayName("빈 sources 배열은 출처로 세지 않는다")
    void emptySourcesArrayDoesNotCount() {
        stubResponse(
                """
                {"blocks": [
                  {"type": "paragraph", "text": "본문", "attributes": {"sources": []}}
                ]}
                """);

        assertThatThrownBy(() -> service.generate(SKELETON_ID, CHAPTER_ID)).isInstanceOf(ExternalApiException.class);
    }

    /** 블록 하나 때문에 챕터 전체를 버리는 것은 과하다 — 남은 블록으로도 본문은 성립한다. */
    @Test
    @DisplayName("알 수 없는 타입과 필수 항목이 빠진 블록은 건너뛰고 나머지를 쓴다")
    void skipsInvalidBlocks() {
        stubResponse(
                """
                {"blocks": [
                  {"type": "paragraph", "text": "본문", "attributes": {"sources": [{"title": "출처"}]}},
                  {"type": "callout", "text": "알 수 없는 타입"},
                  {"type": "quiz", "attributes": {"question": "정답 없는 퀴즈", "choices": ["a"]}}
                ]}
                """);

        service.generate(SKELETON_ID, CHAPTER_ID);

        ArgumentCaptor<RecordChapterBodyCommand> command = ArgumentCaptor.forClass(RecordChapterBodyCommand.class);
        verify(recordChapterBodyUseCase).record(any(), command.capture());
        assertThat(command.getValue().blocks()).extracting(ProposedBlock::type).containsExactly(BlockType.PARAGRAPH);
    }

    @Test
    @DisplayName("쓸 수 있는 블록이 하나도 없으면 실패로 올린다")
    void emptyBlocksFail() {
        stubResponse("{\"blocks\": [{\"type\": \"callout\", \"text\": \"x\"}]}");

        assertThatThrownBy(() -> service.generate(SKELETON_ID, CHAPTER_ID)).isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("프롬프트에 개요·선행 챕터·형제 챕터가 들어간다")
    void promptCarriesContext() {
        stubResponse(VALID_RESPONSE);

        service.generate(SKELETON_ID, CHAPTER_ID);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiGateway).complete(any(), any(), system.capture(), user.capture(), anyInt());
        assertThat(user.getValue())
                .contains("경사의 의미")
                .contains("제목-loss-function")
                .contains("제목-learning-rate");
        // 자기 자신은 "다른 챕터" 목록에 없어야 한다.
        assertThat(user.getValue().split("같은 뼈대의 다른 챕터")[1]).doesNotContain("제목-gradient-descent");
        assertThat(system.getValue()).contains("sources").contains("narration").contains("quiz");
    }

    @Test
    @DisplayName("출처 속성은 도메인에서도 인식된다")
    void domainRecognisesSources() {
        Block sourced = new Block(
                "b1",
                BlockType.PARAGRAPH,
                "본문",
                java.util.Map.of(Block.SOURCES, List.of(java.util.Map.of("title", "출처"))));

        assertThat(sourced.hasSources()).isTrue();
        assertThat(Block.text("b2", BlockType.PARAGRAPH, "본문").hasSources()).isFalse();
    }

    private void stubResponse(String text) {
        when(aiGateway.complete(any(), any(), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult(text, 1200, 3500));
    }

    private static Chapter chapter(Long id, String key, int currentVersion) {
        return new Chapter(id, SKELETON_ID, key, "제목-" + key, "요약", currentVersion, 0, 0, null, null);
    }
}
