package com.mindplates.nextchapter.domain.chapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 블록 ID 규칙은 집단 루프의 정밀도를 결정하므로 여기서 가장 촘촘하게 고정한다.
 *
 * <p>규칙이 지켜야 하는 두 가지 — <b>버전이 올라가도 유지된다</b>, 그리고 <b>재사용되지 않는다</b>.
 * 후자를 어기면 두 버전의 신호가 한 블록에 섞여 에러 없이 잘못된 결론을 만든다.
 */
@DisplayName("블록 문서")
class BlockDocumentTest {

    @Nested
    @DisplayName("문서 불변식")
    class Invariants {

        @Test
        @DisplayName("ID 가 중복된 문서는 만들 수 없다 — 신호가 두 지점에 겹쳐 붙는다")
        void rejectsDuplicateIds() {
            assertThatThrownBy(() -> BlockDocument.of(
                            Block.text("b1", BlockType.HEADING, "제목"), Block.text("b1", BlockType.PARAGRAPH, "본문")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("중복");
        }

        @Test
        @DisplayName("빈 문서는 만들 수 없다")
        void rejectsEmpty() {
            assertThatThrownBy(() -> new BlockDocument(List.of())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("최대 순번을 읽어 카운터를 따라잡을 수 있다")
        void reportsMaxSequence() {
            BlockDocument document = BlockDocument.of(
                    Block.text("b1", BlockType.HEADING, "제목"), Block.text("b7", BlockType.PARAGRAPH, "본문"));

            assertThat(document.maxSequence()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("형태별 블록 걸러내기")
    class DeliveryFiltering {

        private final BlockDocument document = BlockDocument.of(
                Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                Block.text("b2", BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로"),
                Block.text("b3", BlockType.NARRATION, "이제 학습률을 봅시다"),
                Block.quiz("b4", "학습률이 크면?", List.of("발산", "수렴"), 0));

        /** 문서를 형태별로 나누지 않고 걸러 보는 것이 "소스는 하나"의 실제 구현이다. */
        @Test
        @DisplayName("웹은 narration 을 건너뛴다")
        void webSkipsNarration() {
            assertThat(document.webBlocks()).extracting(Block::id).containsExactly("b1", "b2", "b4");
        }

        @Test
        @DisplayName("영상은 narration 만 쓴다")
        void narrationForVideo() {
            assertThat(document.narrationBlocks()).extracting(Block::id).containsExactly("b3");
        }

        @Test
        @DisplayName("퀴즈 블록을 따로 뽑을 수 있다 — 오답률 신호의 대상이다")
        void quizBlocks() {
            assertThat(document.quizBlocks()).extracting(Block::id).containsExactly("b4");
            assertThat(document.findById("b4")).isPresent();
            assertThat(document.findById("b99")).isEmpty();
        }
    }

    @Nested
    @DisplayName("ID 승계")
    class Succession {

        private final BlockDocument previous = BlockDocument.of(
                Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                Block.text("b2", BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로"),
                Block.text("b3", BlockType.PARAGRAPH, "학습률이란"));

        @Test
        @DisplayName("신규 생성은 1번부터 발급한다")
        void assignsFromOneOnCreate() {
            BlockDocument.Succession result = BlockDocument.succeed(
                    List.of(ProposedBlock.text(BlockType.HEADING, "제목"), ProposedBlock.text(BlockType.PARAGRAPH, "본문")),
                    null,
                    1);

            assertThat(result.document().blocks()).extracting(Block::id).containsExactly("b1", "b2");
            assertThat(result.nextSequence()).isEqualTo(3);
        }

        @Test
        @DisplayName("모델이 돌려준 기존 ID 는 그대로 승계된다 — 수정 전후 신호가 이어진다")
        void inheritsKnownIds() {
            BlockDocument.Succession result = BlockDocument.succeed(
                    List.of(
                            ProposedBlock.inheriting("b1", BlockType.HEADING, "경사하강법이란", Map.of()),
                            ProposedBlock.inheriting("b2", BlockType.PARAGRAPH, "더 자세히 고친 설명", Map.of())),
                    previous,
                    4);

            assertThat(result.document().blocks()).extracting(Block::id).containsExactly("b1", "b2");
            assertThat(result.document().findById("b2"))
                    .get()
                    .extracting(Block::text)
                    .isEqualTo("더 자세히 고친 설명");
        }

        @Test
        @DisplayName("모르는 ID 는 버리고 새로 발급한다 — 잘못된 ID 를 통과시키면 다른 블록 신호를 오염시킨다")
        void discardsUnknownIds() {
            BlockDocument.Succession result = BlockDocument.succeed(
                    List.of(
                            ProposedBlock.inheriting("b1", BlockType.HEADING, "경사하강법이란", Map.of()),
                            ProposedBlock.inheriting("b99", BlockType.PARAGRAPH, "새 문단", Map.of())),
                    previous,
                    4);

            assertThat(result.document().blocks()).extracting(Block::id).containsExactly("b1", "b4");
        }

        @Test
        @DisplayName("형식이 틀린 ID 도 새로 발급한다")
        void discardsMalformedIds() {
            BlockDocument.Succession result = BlockDocument.succeed(
                    List.of(ProposedBlock.inheriting("new", BlockType.PARAGRAPH, "본문", Map.of())), previous, 4);

            assertThat(result.document().blocks()).extracting(Block::id).containsExactly("b4");
        }

        @Test
        @DisplayName("한 문서에서 같은 ID 를 두 번 쓰면 두 번째는 새로 발급한다")
        void secondUseOfSameIdGetsNewId() {
            BlockDocument.Succession result = BlockDocument.succeed(
                    List.of(
                            ProposedBlock.inheriting("b1", BlockType.PARAGRAPH, "첫 번째", Map.of()),
                            ProposedBlock.inheriting("b1", BlockType.PARAGRAPH, "두 번째", Map.of())),
                    previous,
                    4);

            assertThat(result.document().blocks()).extracting(Block::id).containsExactly("b1", "b4");
        }

        /** 삭제된 블록의 ID 는 비어 있는 채로 남고 다시 쓰이지 않는다. */
        @Test
        @DisplayName("블록을 지웠다가 다시 만들어도 지워진 ID 가 되살아나지 않는다")
        void deletedIdsAreNotRecycled() {
            // b3 를 지운 v2
            BlockDocument.Succession v2 = BlockDocument.succeed(
                    List.of(
                            ProposedBlock.inheriting("b1", BlockType.HEADING, "경사하강법이란", Map.of()),
                            ProposedBlock.inheriting("b2", BlockType.PARAGRAPH, "본문", Map.of())),
                    previous,
                    4);

            // v3 에서 문단을 하나 추가 — b3 가 아니라 b4 를 받아야 한다
            BlockDocument.Succession v3 = BlockDocument.succeed(
                    List.of(
                            ProposedBlock.inheriting("b1", BlockType.HEADING, "경사하강법이란", Map.of()),
                            ProposedBlock.inheriting("b2", BlockType.PARAGRAPH, "본문", Map.of()),
                            ProposedBlock.text(BlockType.PARAGRAPH, "새 문단")),
                    v2.document(),
                    v2.nextSequence());

            assertThat(v3.document().blocks()).extracting(Block::id).containsExactly("b1", "b2", "b4");
        }

        /**
         * 카운터가 문서의 최대 순번보다 뒤처져 있어도 이미 쓰인 ID 를 다시 내주지 않는다. 데이터 이관
         * 이나 카운터 초기화 실수가 있어도 재사용만은 막혀야 한다.
         */
        @Test
        @DisplayName("카운터가 뒤처져 있으면 문서의 최대 순번을 기준으로 따라잡는다")
        void catchesUpWhenCounterLags() {
            BlockDocument.Succession result = BlockDocument.succeed(
                    List.of(
                            ProposedBlock.inheriting("b1", BlockType.HEADING, "제목", Map.of()),
                            ProposedBlock.text(BlockType.PARAGRAPH, "새 문단")),
                    previous,
                    1);

            assertThat(result.document().blocks()).extracting(Block::id).containsExactly("b1", "b4");
        }

        @Test
        @DisplayName("확정할 블록이 없으면 거절한다")
        void rejectsEmptyProposal() {
            assertThatThrownBy(() -> BlockDocument.succeed(List.of(), previous, 4))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
