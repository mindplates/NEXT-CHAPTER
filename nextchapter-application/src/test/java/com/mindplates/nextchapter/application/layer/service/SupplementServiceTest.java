package com.mindplates.nextchapter.application.layer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.service.SharedChapterDocuments;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.application.generation.service.AiGateway;
import com.mindplates.nextchapter.application.layer.port.out.LoadSupplementBlockPort;
import com.mindplates.nextchapter.application.layer.port.out.SaveSupplementBlockPort;
import com.mindplates.nextchapter.application.layer.port.out.SaveSupplementBlockPort.NewSupplement;
import com.mindplates.nextchapter.application.layer.view.SupplementView;
import com.mindplates.nextchapter.application.signal.port.out.LoadSignalPort;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.layer.model.SupplementBlock;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 보충 블록에서 조용히 틀릴 수 있는 것 둘을 고정한다.
 *
 * <ul>
 *   <li>같은 지점을 다시 요청할 때 또 만들면 비용이 <b>사용자 수 × 조회 수</b>로 늘어난다
 *   <li>빈 응답을 저장하면 그 지점이 영구히 빈칸이 된다 — 다시 요청해도 "이미 있다"로 판정된다
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("보충 블록")
class SupplementServiceTest {

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterVersionPort loadChapterVersionPort;

    @Mock
    ChapterDocumentCachePort chapterDocumentCachePort;

    @Mock
    LoadSignalPort loadSignalPort;

    @Mock
    LoadSupplementBlockPort loadSupplementBlockPort;

    @Mock
    SaveSupplementBlockPort saveSupplementBlockPort;

    @Mock
    AiGateway aiGateway;

    SupplementService service;

    @BeforeEach
    void setUp() {
        service = new SupplementService(
                new PublishedSkeletonGuard(loadSkeletonPort),
                loadChapterPort,
                new SharedChapterDocuments(loadChapterVersionPort, chapterDocumentCachePort),
                loadSignalPort,
                loadSupplementBlockPort,
                saveSupplementBlockPort,
                aiGateway);
    }

    private void stubPublishedChapter() {
        when(loadChapterPort.findById(100L))
                .thenReturn(Optional.of(new Chapter(100L, 5L, "a", "경사하강법", null, 2, 4, 0, null, null)));
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.PUBLISHED, null, null)));
    }

    private void stubDocument() {
        when(chapterDocumentCachePort.find(100L, 2, DeliveryFormat.WEB))
                .thenReturn(Optional.of(new CachedChapterDocument(
                        100L,
                        2,
                        DeliveryFormat.WEB,
                        List.of(
                                Block.text("b1", BlockType.HEADING, "제목"),
                                Block.text("b2", BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로")),
                        false,
                        null,
                        null)));
    }

    private static SupplementBlock supplement(String anchor, String id, String text) {
        return new SupplementBlock(anchor, Block.text(id, BlockType.PARAGRAPH, text));
    }

    @Test
    @DisplayName("막힌 지점에 문단을 만들어 붙인다")
    void createsSupplement() {
        stubPublishedChapter();
        stubDocument();
        when(loadSupplementBlockPort.findByAnchor(42L, 100L, "b2")).thenReturn(Optional.empty());
        when(loadSignalPort.findByUserAndChapter(42L, 100L)).thenReturn(List.of());
        when(aiGateway.complete(eq(AiStage.PERSONALIZATION), eq(5L), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult("예를 들어 공을 굴린다고 생각해 보세요.", 100, 60));
        when(saveSupplementBlockPort.create(any())).thenReturn(supplement("b2", "s1", "예를 들어 공을 굴린다고 생각해 보세요."));

        SupplementView view = service.request(42L, 100L, "b2");

        assertThat(view.reused()).isFalse();
        assertThat(view.anchorBlockId()).isEqualTo("b2");
        assertThat(view.block().id()).isEqualTo("s1");

        ArgumentCaptor<NewSupplement> saved = ArgumentCaptor.forClass(NewSupplement.class);
        verify(saveSupplementBlockPort).create(saved.capture());
        assertThat(saved.getValue().anchorBlockId()).isEqualTo("b2");
        assertThat(saved.getValue().chapterVersion()).isEqualTo(2);
        assertThat(saved.getValue().text()).isEqualTo("예를 들어 공을 굴린다고 생각해 보세요.");
    }

    /** 새로고침 한 번이 LLM 호출 한 번이 되면 비용이 조회 수만큼 늘어난다. */
    @Test
    @DisplayName("같은 지점을 다시 요청하면 모델을 부르지 않는다")
    void reusesExisting() {
        stubPublishedChapter();
        when(loadSupplementBlockPort.findByAnchor(42L, 100L, "b2"))
                .thenReturn(Optional.of(supplement("b2", "s7", "이미 만든 설명")));

        SupplementView view = service.request(42L, 100L, "b2");

        assertThat(view.reused()).isTrue();
        assertThat(view.block().id()).isEqualTo("s7");
        verify(aiGateway, never()).complete(any(), anyLong(), anyString(), anyString(), anyInt());
        verify(saveSupplementBlockPort, never()).create(any());
    }

    /** 빈 보충 블록을 저장하면 다시 요청해도 "이미 있다"로 판정돼 그 지점이 영구히 빈칸이 된다. */
    @Test
    @DisplayName("빈 응답은 저장하지 않는다")
    void rejectsEmptyCompletion() {
        stubPublishedChapter();
        stubDocument();
        when(loadSupplementBlockPort.findByAnchor(42L, 100L, "b2")).thenReturn(Optional.empty());
        when(loadSignalPort.findByUserAndChapter(42L, 100L)).thenReturn(List.of());
        when(aiGateway.complete(eq(AiStage.PERSONALIZATION), eq(5L), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult("   ", 100, 0));

        assertThatThrownBy(() -> service.request(42L, 100L, "b2")).isInstanceOf(ExternalApiException.class);
        verify(saveSupplementBlockPort, never()).create(any());
    }

    /** 보충 블록에 보충을 붙이면 그 순서가 정의되지 않는다. */
    @Test
    @DisplayName("앵커가 보충 블록이면 거절한다")
    void rejectsSupplementAnchor() {
        assertThatThrownBy(() -> service.request(42L, 100L, "s1")).isInstanceOf(InvalidOperationException.class);
        verify(loadChapterPort, never()).findById(anyLong());
    }

    @Test
    @DisplayName("그 버전에 없는 블록은 거절한다")
    void rejectsUnknownAnchor() {
        stubPublishedChapter();
        stubDocument();
        when(loadSupplementBlockPort.findByAnchor(42L, 100L, "b9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(42L, 100L, "b9")).isInstanceOf(InvalidOperationException.class);
        verify(aiGateway, never()).complete(any(), anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("공개되지 않은 뼈대에는 보충을 붙일 수 없다")
    void rejectsUnpublished() {
        when(loadChapterPort.findById(100L))
                .thenReturn(Optional.of(new Chapter(100L, 5L, "a", "제목", null, 2, 4, 0, null, null)));
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.GENERATING_BODIES, null, null)));

        assertThatThrownBy(() -> service.request(42L, 100L, "b2")).isInstanceOf(EntityNotFoundException.class);
    }

    /**
     * AI 호출이 게이트웨이를 지나야 예산 상한 검사를 통과한다. 직접 벤더를 부르면 뼈대당·일일 상한 밖으로
     * 새는 경로가 하나 생긴다. 뼈대 ID 를 함께 넘기는 것도 그 때문이다 — 이 비용도 그 뼈대가 만든 것이다.
     */
    @Test
    @DisplayName("개인화 단계로, 뼈대 ID 를 달고 호출한다")
    void callsGatewayWithSkeletonId() {
        stubPublishedChapter();
        stubDocument();
        when(loadSupplementBlockPort.findByAnchor(42L, 100L, "b2")).thenReturn(Optional.empty());
        when(loadSignalPort.findByUserAndChapter(42L, 100L)).thenReturn(List.of());
        when(aiGateway.complete(any(), anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult("설명", 10, 10));
        when(saveSupplementBlockPort.create(any())).thenReturn(supplement("b2", "s1", "설명"));

        service.request(42L, 100L, "b2");

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(aiGateway).complete(eq(AiStage.PERSONALIZATION), eq(5L), anyString(), userPrompt.capture(), anyInt());
        // 프롬프트에 막힌 지점과 본문이 함께 들어가야 "일반적인 추가 설명"이 나오지 않는다.
        assertThat(userPrompt.getValue()).contains("b2").contains("손실함수를 줄이는 방향으로");
    }

    @Test
    @DisplayName("사용자의 질문이 프롬프트에 들어간다")
    void includesLearnerSignals() {
        stubPublishedChapter();
        stubDocument();
        when(loadSupplementBlockPort.findByAnchor(42L, 100L, "b2")).thenReturn(Optional.empty());
        when(loadSignalPort.findByUserAndChapter(42L, 100L))
                .thenReturn(List.of(new com.mindplates.nextchapter.domain.signal.model.Signal(
                        1L,
                        42L,
                        100L,
                        2,
                        "b2",
                        DeliveryFormat.WEB,
                        com.mindplates.nextchapter.domain.signal.model.SignalType.QUESTION,
                        Map.of("text", "왜 기울기의 반대 방향인가요?"),
                        null,
                        null)));
        when(aiGateway.complete(any(), anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult("설명", 10, 10));
        when(saveSupplementBlockPort.create(any())).thenReturn(supplement("b2", "s1", "설명"));

        service.request(42L, 100L, "b2");

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(aiGateway).complete(any(), anyLong(), anyString(), userPrompt.capture(), anyInt());
        assertThat(userPrompt.getValue()).contains("왜 기울기의 반대 방향인가요?");
    }
}
