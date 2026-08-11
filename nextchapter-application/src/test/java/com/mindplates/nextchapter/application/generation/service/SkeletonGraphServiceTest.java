package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterRelationPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
@DisplayName("① 그래프 생성")
class SkeletonGraphServiceTest {

    private static final Long SKELETON_ID = 5L;

    private static final String VALID_RESPONSE =
            """
            {
              "chapters": [
                {"key": "loss-function", "title": "손실함수", "summary": "예측과 정답의 차이"},
                {"key": "gradient-descent", "title": "경사하강법", "summary": "손실을 줄이는 방향"},
                {"key": "learning-rate", "title": "학습률", "summary": "한 걸음의 크기"},
                {"key": "overfitting", "title": "과적합", "summary": "훈련 데이터에만 맞는 상태"}
              ],
              "relations": [
                {"from": "loss-function", "to": "gradient-descent", "type": "prerequisite"},
                {"from": "gradient-descent", "to": "learning-rate", "type": "prerequisite"},
                {"from": "overfitting", "to": "learning-rate", "type": "related"}
              ]
            }
            """;

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadCatalogTopicPort loadCatalogTopicPort;

    @Mock
    LoadCatalogFieldPort loadCatalogFieldPort;

    @Mock
    LoadCatalogDomainPort loadCatalogDomainPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    SaveChapterPort saveChapterPort;

    @Mock
    SaveChapterRelationPort saveChapterRelationPort;

    @Mock
    com.mindplates.nextchapter.application.chapter.port.out.SaveOutboxEventPort saveOutboxEventPort;

    @Mock
    AiGateway aiGateway;

    @InjectMocks
    SkeletonGraphService service;

    @BeforeEach
    void setUpCatalog() {
        when(loadSkeletonPort.findById(SKELETON_ID)).thenReturn(Optional.of(Skeleton.start(42L)));
        when(loadCatalogTopicPort.findById(42L))
                .thenReturn(Optional.of(new CatalogTopic(42L, 20L, "machine-learning", "머신러닝", "설명", 0, null, null)));
        when(loadCatalogFieldPort.findById(20L))
                .thenReturn(Optional.of(new CatalogField(20L, 10L, "ai", "인공지능", null, 0, null, null)));
        when(loadCatalogDomainPort.findById(10L))
                .thenReturn(Optional.of(new CatalogDomain(10L, "cs", "컴퓨터과학", null, 0, null, null)));
        when(loadChapterPort.findBySkeletonId(SKELETON_ID)).thenReturn(List.of());

        AtomicLong nextId = new AtomicLong(100);
        when(saveChapterPort.save(any())).thenAnswer(invocation -> {
            Chapter chapter = invocation.getArgument(0);
            Long id = chapter.id() == null ? nextId.getAndIncrement() : chapter.id();
            return new Chapter(
                    id,
                    chapter.skeletonId(),
                    chapter.chapterKey(),
                    chapter.title(),
                    chapter.summary(),
                    chapter.currentVersion(),
                    chapter.blockIdSequence(),
                    chapter.sortOrder(),
                    null,
                    null);
        });
    }

    @Test
    @DisplayName("챕터와 관계를 저장하고 챕터 수를 돌려준다")
    void savesChaptersAndRelations() {
        stubResponse(VALID_RESPONSE);

        int chapters = service.generate(SKELETON_ID);

        assertThat(chapters).isEqualTo(4);
        ArgumentCaptor<List<ChapterRelation>> saved = ArgumentCaptor.forClass(List.class);
        verify(saveChapterRelationPort).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(3);
        assertThat(saved.getValue())
                .extracting(ChapterRelation::relationType)
                .containsExactly(
                        ChapterRelationType.PREREQUISITE,
                        ChapterRelationType.PREREQUISITE,
                        ChapterRelationType.RELATED);
    }

    /** 옛 간선이 새 그래프에 섞이면 순환이 생길 수 있고, 그 순환은 생성 시점 검증을 지나온 뒤라 안 걸린다. */
    /** Neo4j 를 직접 쓰지 않는다 — 분산 트랜잭션이 없어 한쪽만 성공하면 불일치가 남는다. */
    @Test
    @DisplayName("관계와 같은 커밋에 outbox 이벤트를 남긴다")
    void appendsGraphOutboxEvent() {
        stubResponse(VALID_RESPONSE);

        service.generate(SKELETON_ID);

        ArgumentCaptor<com.mindplates.nextchapter.domain.chapter.model.OutboxEvent> event =
                ArgumentCaptor.forClass(com.mindplates.nextchapter.domain.chapter.model.OutboxEvent.class);
        verify(saveOutboxEventPort).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo(com.mindplates.nextchapter.domain.chapter.model.OutboxEventType.CHAPTER_GRAPH_PERSISTED);
        assertThat(event.getValue().partitionKey()).isEqualTo(String.valueOf(SKELETON_ID));
        assertThat(event.getValue().payload()).contains("PREREQUISITE");
    }

    @Test
    @DisplayName("재생성은 관계를 먼저 지운다 — 덮어쓰기여야 한다")
    void replacesRelationsOnRegenerate() {
        stubResponse(VALID_RESPONSE);

        service.generate(SKELETON_ID);

        verify(saveChapterRelationPort).deleteBySkeletonId(SKELETON_ID);
    }

    /** 챕터 ID 가 갈리면 사용자 레이어와 집단 신호가 끊긴다 — 재생성이 그것을 지울 수는 없다. */
    @Test
    @DisplayName("같은 키의 챕터는 ID 를 유지하고 제목·요약만 갱신한다")
    void reusesChapterIdsByKey() {
        when(loadChapterPort.findBySkeletonId(SKELETON_ID))
                .thenReturn(
                        List.of(new Chapter(777L, SKELETON_ID, "loss-function", "옛 제목", "옛 요약", 3, 9, 0, null, null)));
        stubResponse(VALID_RESPONSE);

        service.generate(SKELETON_ID);

        ArgumentCaptor<Chapter> saved = ArgumentCaptor.forClass(Chapter.class);
        verify(saveChapterPort, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        Chapter reused = saved.getAllValues().stream()
                .filter(chapter -> "loss-function".equals(chapter.chapterKey()))
                .findFirst()
                .orElseThrow();
        assertThat(reused.id()).isEqualTo(777L);
        assertThat(reused.title()).isEqualTo("손실함수");
        // 버전과 블록 ID 카운터도 살아남아야 한다 — 본문과 신호가 거기에 매달려 있다.
        assertThat(reused.currentVersion()).isEqualTo(3);
        assertThat(reused.blockIdSequence()).isEqualTo(9);
    }

    @Test
    @DisplayName("알 수 없는 관계 타입은 건너뛰고 나머지는 저장한다")
    void skipsUnknownRelationTypes() {
        stubResponse(
                """
                {
                  "chapters": [
                    {"key": "a", "title": "A"}, {"key": "b", "title": "B"},
                    {"key": "c", "title": "C"}, {"key": "d", "title": "D"}
                  ],
                  "relations": [
                    {"from": "a", "to": "b", "type": "prerequisite"},
                    {"from": "b", "to": "c", "type": "leads-to"}
                  ]
                }
                """);

        service.generate(SKELETON_ID);

        ArgumentCaptor<List<ChapterRelation>> saved = ArgumentCaptor.forClass(List.class);
        verify(saveChapterRelationPort).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
    }

    /** 매핑 단계와 달리 대안 경로가 없다. 빈 그래프를 흘려보내면 빈 뼈대가 published 로 간다. */
    @Test
    @DisplayName("응답을 파싱할 수 없으면 실패로 올린다 — 조용히 빈 그래프를 만들지 않는다")
    void parseFailureIsRaised() {
        stubResponse("설명만 있고 JSON 이 없는 응답");

        assertThatThrownBy(() -> service.generate(SKELETON_ID)).isInstanceOf(ExternalApiException.class);
        verify(saveChapterPort, never()).save(any());
    }

    @Test
    @DisplayName("순환이 있는 그래프는 저장되지 않는다")
    void cyclicGraphIsRejected() {
        stubResponse(
                """
                {
                  "chapters": [
                    {"key": "a", "title": "A"}, {"key": "b", "title": "B"},
                    {"key": "c", "title": "C"}, {"key": "d", "title": "D"}
                  ],
                  "relations": [
                    {"from": "a", "to": "b", "type": "prerequisite"},
                    {"from": "b", "to": "a", "type": "prerequisite"}
                  ]
                }
                """);

        assertThatThrownBy(() -> service.generate(SKELETON_ID)).isInstanceOf(IllegalArgumentException.class);
        verify(saveChapterPort, never()).save(any());
    }

    @Test
    @DisplayName("챕터가 하한보다 적은 응답은 거절된다")
    void tooFewChaptersRejected() {
        stubResponse("{\"chapters\": [{\"key\": \"a\", \"title\": \"A\"}], \"relations\": []}");

        assertThatThrownBy(() -> service.generate(SKELETON_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 지시하지 않으면 모델은 거의 항상 1장·2장 형태의 목차를 내놓는다. */
    @Test
    @DisplayName("프롬프트가 선형 목차를 금지한다")
    void promptForbidsLinearOutline() {
        stubResponse(VALID_RESPONSE);

        service.generate(SKELETON_ID);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiGateway).complete(any(), system.capture(), user.capture(), anyInt());
        assertThat(system.getValue()).contains("선형 목차를 만들지 마라").contains("순환");
        assertThat(user.getValue()).contains("머신러닝").contains("컴퓨터과학").contains("인공지능");
    }

    private void stubResponse(String text) {
        when(aiGateway.complete(any(), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult(text, 500, 1500));
    }
}
