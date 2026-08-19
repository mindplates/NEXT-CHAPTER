package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.out.LoadRevisionProposalPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveRevisionProposalPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.application.signal.port.out.LoadCollectiveSignalAggregatePort;
import com.mindplates.nextchapter.application.signal.port.out.LoadRevisionTriggerPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveRevisionTriggerPort;
import com.mindplates.nextchapter.domain.admin.model.RevisionProposal;
import com.mindplates.nextchapter.domain.admin.model.RevisionProposalStatus;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
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
@DisplayName("P5.3 수정안 생성")
class ChapterRevisionProposalServiceTest {

    private static final Long CHAPTER_ID = 100L;
    private static final Long SKELETON_ID = 5L;

    private static final String VALID_RESPONSE =
            """
            {
              "rationale": "학습률 설명이 갑작스러워 예시를 추가했다.",
              "blocks": [
                {"id": "b1", "type": "heading", "text": "경사하강법이란"},
                {"id": "b6", "type": "paragraph", "text": "학습률이 크면 발산할 수 있다 — 예: ...",
                 "attributes": {"sources": [{"title": "Deep Learning", "quote": "learning rate"}]}}
              ]
            }
            """;

    @Mock
    LoadRevisionTriggerPort loadRevisionTriggerPort;

    @Mock
    SaveRevisionTriggerPort saveRevisionTriggerPort;

    @Mock
    LoadRevisionProposalPort loadRevisionProposalPort;

    @Mock
    SaveRevisionProposalPort saveRevisionProposalPort;

    @Mock
    LoadCollectiveSignalAggregatePort loadCollectiveSignalAggregatePort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterVersionPort loadChapterVersionPort;

    @Mock
    AiGateway aiGateway;

    @InjectMocks
    ChapterRevisionProposalService service;

    private static RevisionTrigger trigger() {
        return new RevisionTrigger(1L, CHAPTER_ID, 2, "b6", 5, 20, 9, null);
    }

    @BeforeEach
    void setUp() {
        when(loadRevisionTriggerPort.claimUnprocessed(10)).thenReturn(List.of(trigger()));
        when(loadRevisionProposalPort.findByTriggerId(1L)).thenReturn(Optional.empty());
        when(loadChapterPort.findById(CHAPTER_ID)).thenReturn(Optional.of(chapter(2)));
        when(loadChapterVersionPort.find(CHAPTER_ID, 2)).thenReturn(Optional.of(chapterVersion()));
        when(loadCollectiveSignalAggregatePort.formatBreakdown(CHAPTER_ID, 2, "b6"))
                .thenReturn(List.of());
        stubResponse(VALID_RESPONSE);
    }

    @Test
    @DisplayName("검증을 통과하면 승인 대기 상태의 제안을 저장한다")
    void savesPendingApprovalProposal() {
        assertThat(service.generatePending(10)).isEqualTo(1);

        ArgumentCaptor<RevisionProposal> captor = ArgumentCaptor.forClass(RevisionProposal.class);
        verify(saveRevisionProposalPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RevisionProposalStatus.PENDING_APPROVAL);
        assertThat(captor.getValue().rationale()).contains("학습률");
        assertThat(captor.getValue().triggerId()).isEqualTo(1L);
        verify(saveRevisionTriggerPort).markProcessed(1L);
    }

    /** 검증 실패한 본문을 승인 대기열에 그대로 올리면 검증 패스를 둔 의미가 없다. */
    @Test
    @DisplayName("출처가 없으면 검증 실패 상태로 저장한다")
    void savesVerificationFailedWhenNoSources() {
        stubResponse(
                """
                {"rationale": "고쳤다", "blocks": [{"id": "b6", "type": "paragraph", "text": "출처 없는 본문"}]}
                """);

        service.generatePending(10);

        ArgumentCaptor<RevisionProposal> captor = ArgumentCaptor.forClass(RevisionProposal.class);
        verify(saveRevisionProposalPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RevisionProposalStatus.VERIFICATION_FAILED);
        verify(saveRevisionTriggerPort).markProcessed(1L);
    }

    /** Kafka 와 같은 이유다 — 폴링이 같은 트리거를 다시 집어도 제안이 중복 생성되면 안 된다. */
    @Test
    @DisplayName("이미 제안이 있으면 다시 생성하지 않는다")
    void skipsWhenProposalAlreadyExists() {
        when(loadRevisionProposalPort.findByTriggerId(1L))
                .thenReturn(Optional.of(RevisionProposal.verified(
                        1L,
                        CHAPTER_ID,
                        2,
                        "b6",
                        "근거",
                        List.of(),
                        List.of(com.mindplates.nextchapter.domain.chapter.model.ProposedBlock.text(
                                BlockType.PARAGRAPH, "x")))));

        assertThat(service.generatePending(10)).isEqualTo(1);

        verify(aiGateway, never()).complete(any(), any(), anyString(), anyString(), anyInt());
        verify(saveRevisionTriggerPort).markProcessed(1L);
    }

    /** 트리거 발생 후 챕터가 이미 다른 경로로 바뀌었다면, 낡은 근거로 만든 제안은 그 변경을 덮어쓰게 된다. */
    @Test
    @DisplayName("챕터가 이미 다른 버전으로 바뀌었으면 건너뛴다")
    void skipsStaleChapterVersion() {
        when(loadChapterPort.findById(CHAPTER_ID)).thenReturn(Optional.of(chapter(3)));

        service.generatePending(10);

        verify(aiGateway, never()).complete(any(), any(), anyString(), anyString(), anyInt());
        verify(saveRevisionProposalPort, never()).save(any());
        verify(saveRevisionTriggerPort).markProcessed(1L);
    }

    /** 실패한 트리거 하나가 나머지를 막지 않는다 — 표시하지 않아 다음 폴링이 다시 시도한다. */
    @Test
    @DisplayName("AI 호출이 실패해도 예외를 올리지 않고 건너뛴다")
    void isolatesFailureOfOneTrigger() {
        when(aiGateway.complete(any(), any(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(service.generatePending(10)).isZero();

        verify(saveRevisionTriggerPort, never()).markProcessed(1L);
    }

    private void stubResponse(String text) {
        when(aiGateway.complete(any(), any(), anyString(), anyString(), anyInt()))
                .thenReturn(new LlmCompletionResult(text, 1200, 3500));
    }

    private static Chapter chapter(int currentVersion) {
        return new Chapter(
                CHAPTER_ID, SKELETON_ID, "gradient-descent", "경사하강법", "요약", currentVersion, 6, 0, null, null);
    }

    private static ChapterVersion chapterVersion() {
        return new ChapterVersion(
                1L,
                CHAPTER_ID,
                2,
                BlockDocument.of(
                        Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                        Block.text("b6", BlockType.PARAGRAPH, "학습률이 크면?")),
                null,
                ChapterVersionSource.GENERATED,
                "pipeline",
                null);
    }
}
