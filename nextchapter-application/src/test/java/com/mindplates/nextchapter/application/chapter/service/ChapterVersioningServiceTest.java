package com.mindplates.nextchapter.application.chapter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.in.command.RecordChapterBodyCommand;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.view.ChapterBodyView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@DisplayName("챕터 본문 기록과 버전 조회")
class ChapterVersioningServiceTest {

    private static final Long CHAPTER_ID = 100L;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    SaveChapterPort saveChapterPort;

    @Mock
    LoadChapterVersionPort loadChapterVersionPort;

    @Mock
    SaveChapterVersionPort saveChapterVersionPort;

    @Mock
    com.mindplates.nextchapter.application.chapter.port.out.SaveOutboxEventPort saveOutboxEventPort;

    @InjectMocks
    ChapterVersioningService service;

    @Test
    @DisplayName("최초 기록은 v1 이고 블록 ID 를 1번부터 발급한다")
    void firstRecordIsVersionOne() {
        stubChapter(chapter(0, 0));
        when(loadChapterVersionPort.findLatest(CHAPTER_ID)).thenReturn(Optional.empty());
        when(saveChapterVersionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChapterVersion saved = service.record(
                CHAPTER_ID,
                new RecordChapterBodyCommand(
                        List.of(
                                ProposedBlock.text(BlockType.HEADING, "경사하강법이란"),
                                ProposedBlock.text(BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로")),
                        ChapterVersionSource.GENERATED,
                        null,
                        null));

        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.body().blocks()).extracting(Block::id).containsExactly("b1", "b2");
    }

    /** 스냅샷·현재 버전·카운터가 한 트랜잭션에서 함께 움직여야 한다. */
    @Test
    @DisplayName("기록과 함께 챕터의 현재 버전과 카운터가 갱신된다")
    void updatesChapterAlongsideVersion() {
        stubChapter(chapter(0, 0));
        when(loadChapterVersionPort.findLatest(CHAPTER_ID)).thenReturn(Optional.empty());
        when(saveChapterVersionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(
                CHAPTER_ID,
                new RecordChapterBodyCommand(
                        List.of(
                                ProposedBlock.text(BlockType.HEADING, "제목"),
                                ProposedBlock.text(BlockType.PARAGRAPH, "본문"),
                                ProposedBlock.text(BlockType.NARRATION, "대본")),
                        ChapterVersionSource.GENERATED,
                        null,
                        null));

        ArgumentCaptor<Chapter> saved = ArgumentCaptor.forClass(Chapter.class);
        verify(saveChapterPort).save(saved.capture());
        assertThat(saved.getValue().currentVersion()).isEqualTo(1);
        assertThat(saved.getValue().blockIdSequence()).isEqualTo(3);
    }

    /**
     * outbox 행이 본문·버전과 같은 커밋에 들어가는 것이 outbox 패턴의 전부다. 별도 경로로 빠지면 두 단계
     * 쓰기가 되어 애초에 피하려던 불일치가 돌아온다.
     */
    @Test
    @DisplayName("본문 기록과 함께 outbox 이벤트가 남는다")
    void appendsOutboxEventInSameCall() {
        stubChapter(chapter(0, 0));
        when(loadChapterVersionPort.findLatest(CHAPTER_ID)).thenReturn(Optional.empty());
        when(saveChapterVersionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(
                CHAPTER_ID,
                new RecordChapterBodyCommand(
                        List.of(ProposedBlock.text(BlockType.PARAGRAPH, "본문")),
                        ChapterVersionSource.GENERATED,
                        null,
                        null));

        ArgumentCaptor<com.mindplates.nextchapter.domain.chapter.model.OutboxEvent> event =
                ArgumentCaptor.forClass(com.mindplates.nextchapter.domain.chapter.model.OutboxEvent.class);
        verify(saveOutboxEventPort).append(event.capture());
        // 멱등 키에 버전이 들어가야 재발행이 옛 상태로 되돌리지 않는다.
        assertThat(event.getValue().idempotencyKey()).isEqualTo("chapter:100:v1");
        assertThat(event.getValue().partitionKey()).isEqualTo("1");
        assertThat(event.getValue().payload()).contains("gradient-descent").contains("\"version\":1");
    }

    @Test
    @DisplayName("수정은 v2 가 되고 유지된 블록 ID 를 승계한다 — 수정 전후 신호가 이어진다")
    void revisionInheritsBlockIds() {
        stubChapter(chapter(1, 3));
        when(loadChapterVersionPort.findLatest(CHAPTER_ID)).thenReturn(Optional.of(version(1)));
        when(saveChapterVersionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChapterVersion saved = service.record(
                CHAPTER_ID,
                new RecordChapterBodyCommand(
                        List.of(
                                ProposedBlock.inheriting("b1", BlockType.HEADING, "경사하강법이란", Map.of()),
                                ProposedBlock.inheriting("b2", BlockType.PARAGRAPH, "더 자세히 고친 설명", Map.of()),
                                ProposedBlock.text(BlockType.PARAGRAPH, "새로 붙인 예시")),
                        ChapterVersionSource.REVISED_BY_SIGNAL,
                        "b2 에서 오답이 몰려 설명을 보강했다",
                        "signal-loop"));

        assertThat(saved.version()).isEqualTo(2);
        assertThat(saved.body().blocks()).extracting(Block::id).containsExactly("b1", "b2", "b4");
        assertThat(saved.changeSummary()).isEqualTo("b2 에서 오답이 몰려 설명을 보강했다");
        assertThat(saved.isRevision()).isTrue();
    }

    @Test
    @DisplayName("본문 조회는 narration 을 걸러 내리고 소비한 버전을 함께 준다")
    void currentBodyFiltersNarrationAndCarriesVersion() {
        stubChapter(chapter(1, 3));
        when(loadChapterVersionPort.findLatest(CHAPTER_ID)).thenReturn(Optional.of(version(1)));

        ChapterBodyView view = service.currentBody(CHAPTER_ID);

        assertThat(view.version()).isEqualTo(1);
        assertThat(view.blocks()).extracting(Block::id).containsExactly("b1", "b2");
        assertThat(view.improvedFromPrevious()).isFalse();
        assertThat(view.chapterKey()).isEqualTo("gradient-descent");
    }

    @Test
    @DisplayName("임의 버전을 조회할 수 있다 — 수정 전후 비교의 재료")
    void readsSpecificVersion() {
        stubChapter(chapter(2, 4));
        when(loadChapterVersionPort.find(CHAPTER_ID, 1)).thenReturn(Optional.of(version(1)));

        assertThat(service.bodyAt(CHAPTER_ID, 1).version()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 버전은 404 로 이어진다")
    void missingVersion() {
        stubChapter(chapter(1, 3));
        when(loadChapterVersionPort.find(CHAPTER_ID, 9)).thenReturn(Optional.empty());
        when(loadChapterVersionPort.findLatest(CHAPTER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bodyAt(CHAPTER_ID, 9)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.currentBody(CHAPTER_ID)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("없는 챕터에는 기록할 수 없다")
    void missingChapter() {
        when(loadChapterPort.findById(CHAPTER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(
                        CHAPTER_ID,
                        new RecordChapterBodyCommand(
                                List.of(ProposedBlock.text(BlockType.PARAGRAPH, "본문")),
                                ChapterVersionSource.GENERATED,
                                null,
                                null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("이력은 본문 없이 무엇이 왜 바뀌었는지만 준다")
    void historyCarriesMetadataOnly() {
        when(loadChapterVersionPort.findAllByChapterId(CHAPTER_ID)).thenReturn(List.of(revision(2), version(1)));

        assertThat(service.history(CHAPTER_ID))
                .extracting(view -> view.version() + ":" + view.source() + ":" + view.blockCount())
                .containsExactly("2:REVISED_BY_SIGNAL:2", "1:GENERATED:3");
    }

    @Test
    @DisplayName("키로 챕터를 찾을 수 있다 — 그래프 생성이 ID 없이 관계를 표현한다")
    void findsByKey() {
        when(loadChapterPort.findBySkeletonIdAndKey(1L, "gradient-descent")).thenReturn(Optional.of(chapter(1, 3)));

        assertThat(service.findByKey(1L, "gradient-descent")).isPresent();
    }

    private void stubChapter(Chapter chapter) {
        when(loadChapterPort.findById(CHAPTER_ID)).thenReturn(Optional.of(chapter));
    }

    private static Chapter chapter(int currentVersion, int blockIdSequence) {
        return new Chapter(
                CHAPTER_ID, 1L, "gradient-descent", "경사하강법", "요약", currentVersion, blockIdSequence, 0, null, null);
    }

    private static ChapterVersion version(int number) {
        BlockDocument body = BlockDocument.of(
                Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                Block.text("b2", BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로"),
                Block.text("b3", BlockType.NARRATION, "이제 학습률을 봅시다"));
        return new ChapterVersion(1L, CHAPTER_ID, number, body, null, ChapterVersionSource.GENERATED, null, null);
    }

    private static ChapterVersion revision(int number) {
        BlockDocument body = BlockDocument.of(
                Block.text("b1", BlockType.HEADING, "경사하강법이란"), Block.text("b2", BlockType.PARAGRAPH, "보강한 설명"));
        return new ChapterVersion(
                2L, CHAPTER_ID, number, body, "설명 보강", ChapterVersionSource.REVISED_BY_SIGNAL, "signal-loop", null);
    }
}
