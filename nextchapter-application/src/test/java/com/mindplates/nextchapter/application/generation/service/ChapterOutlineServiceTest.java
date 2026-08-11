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
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterRelationPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterOutlinePort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveOutlineFindingPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFinding;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFindingType;
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
@DisplayName("② 개요 생성")
class ChapterOutlineServiceTest {

    private static final Long SKELETON_ID = 5L;

    private static final String VALID_RESPONSE =
            """
            {
              "outlines": [
                {"chapterKey": "loss-function", "points": ["손실의 정의", "대표적인 손실함수"]},
                {"chapterKey": "gradient-descent", "points": ["경사의 의미", "갱신 규칙", "수렴 조건"]}
              ],
              "findings": [
                {"type": "duplicate", "chapterKeys": ["loss-function", "gradient-descent"],
                 "detail": "두 챕터가 미분을 모두 설명한다"},
                {"type": "gap", "chapterKeys": [], "detail": "정규화를 다루는 챕터가 없다"}
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
    LoadChapterRelationPort loadChapterRelationPort;

    @Mock
    SaveChapterOutlinePort saveChapterOutlinePort;

    @Mock
    SaveOutlineFindingPort saveOutlineFindingPort;

    @Mock
    AiGateway aiGateway;

    @InjectMocks
    ChapterOutlineService service;

    @BeforeEach
    void setUp() {
        when(loadSkeletonPort.findById(SKELETON_ID)).thenReturn(Optional.of(Skeleton.start(42L)));
        when(loadCatalogTopicPort.findById(42L))
                .thenReturn(Optional.of(new CatalogTopic(42L, 20L, "machine-learning", "머신러닝", null, 0, null, null)));
        when(loadChapterPort.findBySkeletonId(SKELETON_ID))
                .thenReturn(List.of(chapter(100L, "loss-function"), chapter(101L, "gradient-descent")));
        when(loadChapterRelationPort.findBySkeletonId(SKELETON_ID))
                .thenReturn(List.of(
                        new ChapterRelation(1L, SKELETON_ID, 100L, 101L, ChapterRelationType.PREREQUISITE, null)));
    }

    @Test
    @DisplayName("챕터별 개요를 저장하고 개수를 돌려준다")
    void savesOutlines() {
        stubResponse(VALID_RESPONSE);

        int count = service.generate(SKELETON_ID);

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<List<ChapterOutline>> saved = ArgumentCaptor.forClass(List.class);
        verify(saveChapterOutlinePort).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ChapterOutline::chapterId).containsExactly(100L, 101L);
        assertThat(saved.getValue().get(1).points()).containsExactly("경사의 의미", "갱신 규칙", "수렴 조건");
    }

    /** 소견을 기록하고도 계속 가는 것이 이 단계의 정책이다 — 오탐 하나가 파이프라인을 세우면 안 된다. */
    @Test
    @DisplayName("중복·누락 소견을 기록하지만 생성을 막지 않는다")
    void recordsFindingsWithoutBlocking() {
        stubResponse(VALID_RESPONSE);

        int count = service.generate(SKELETON_ID);

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<List<OutlineFinding>> saved = ArgumentCaptor.forClass(List.class);
        verify(saveOutlineFindingPort).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(OutlineFinding::findingType)
                .containsExactly(OutlineFindingType.DUPLICATE, OutlineFindingType.GAP);
        assertThat(saved.getValue().get(0).chapterKeys()).containsExactly("loss-function", "gradient-descent");
    }

    @Test
    @DisplayName("재생성은 옛 소견을 먼저 지운다 — 이미 고친 겹침이 남아 있으면 판단을 흐린다")
    void clearsOldFindings() {
        stubResponse(VALID_RESPONSE);

        service.generate(SKELETON_ID);

        verify(saveOutlineFindingPort).deleteBySkeletonId(SKELETON_ID);
    }

    /** 빠진 챕터는 본문을 쓸 재료가 없다는 뜻이다. 어림이 아니라 사실이라 막는다. */
    @Test
    @DisplayName("개요가 빠진 챕터가 있으면 거절한다")
    void rejectsMissingChapterCoverage() {
        stubResponse("{\"outlines\": [{\"chapterKey\": \"loss-function\", \"points\": [\"a\", \"b\"]}]}");

        assertThatThrownBy(() -> service.generate(SKELETON_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("개요가 없는 챕터");
        verify(saveChapterOutlinePort, never()).saveAll(any());
    }

    @Test
    @DisplayName("한 챕터에 개요가 두 개면 거절한다 — 어느 것이 진짜인지 알 수 없다")
    void rejectsDuplicateCoverage() {
        stubResponse(
                """
                {"outlines": [
                  {"chapterKey": "loss-function", "points": ["a", "b"]},
                  {"chapterKey": "loss-function", "points": ["c", "d"]},
                  {"chapterKey": "gradient-descent", "points": ["e", "f"]}
                ]}
                """);

        assertThatThrownBy(() -> service.generate(SKELETON_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("개요가 두 개");
    }

    @Test
    @DisplayName("모르는 챕터를 가리키는 개요는 거절한다")
    void rejectsUnknownChapter() {
        stubResponse(
                """
                {"outlines": [
                  {"chapterKey": "loss-function", "points": ["a", "b"]},
                  {"chapterKey": "gradient-descent", "points": ["c", "d"]},
                  {"chapterKey": "zzz", "points": ["e", "f"]}
                ]}
                """);

        assertThatThrownBy(() -> service.generate(SKELETON_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("항목이 하한보다 적은 개요는 거절한다")
    void rejectsTooFewPoints() {
        stubResponse(
                """
                {"outlines": [
                  {"chapterKey": "loss-function", "points": ["하나뿐"]},
                  {"chapterKey": "gradient-descent", "points": ["a", "b"]}
                ]}
                """);

        assertThatThrownBy(() -> service.generate(SKELETON_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(ChapterOutline.MIN_POINTS));
    }

    @Test
    @DisplayName("응답을 파싱할 수 없으면 실패로 올린다")
    void parseFailureIsRaised() {
        stubResponse("JSON 이 없는 설명");

        assertThatThrownBy(() -> service.generate(SKELETON_ID)).isInstanceOf(ExternalApiException.class);
    }

    /** 개요를 쓸 대상이 없는데 넘어가면 완료 판정이 즉시 참이 되어 빈 뼈대가 published 로 간다. */
    @Test
    @DisplayName("챕터가 없으면 개요 단계 자체를 실패로 만든다")
    void failsWithoutChapters() {
        when(loadChapterPort.findBySkeletonId(SKELETON_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(SKELETON_ID)).isInstanceOf(InvalidOperationException.class);
        verify(aiGateway, never()).complete(any(), any(), anyString(), anyString(), anyInt());
    }

    /** 겹침을 만들지 말라고만 하면 모델은 겹침을 숨긴다. 보고 항목이 응답 형식에 있어야 드러난다. */
    @Test
    @DisplayName("프롬프트가 겹침 보고를 요구하고 관계를 함께 넣는다")
    void promptAsksForFindingsAndCarriesRelations() {
        stubResponse(VALID_RESPONSE);

        service.generate(SKELETON_ID);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiGateway).complete(any(), any(), system.capture(), user.capture(), anyInt());
        assertThat(system.getValue()).contains("findings 에 보고하라").contains("본문을 쓰지 말고");
        assertThat(user.getValue())
                .contains("loss-function → gradient-descent (prerequisite)")
                .contains("머신러닝");
    }

    private void stubResponse(String text) {
        when(aiGateway.complete(any(), any(), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult(text, 800, 2500));
    }

    private static Chapter chapter(Long id, String key) {
        return new Chapter(id, SKELETON_ID, key, "제목-" + key, "요약", 0, 0, 0, null, null);
    }
}
