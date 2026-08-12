package com.mindplates.nextchapter.domain.chapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("블록과 챕터")
class BlockAndChapterTest {

    @Nested
    @DisplayName("블록 검증")
    class BlockValidation {

        @Test
        @DisplayName("텍스트가 필요한 타입은 빈 텍스트를 거절한다")
        void textRequiredTypes() {
            assertThatThrownBy(() -> Block.text("b1", BlockType.PARAGRAPH, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("텍스트");
            assertThatThrownBy(() -> Block.text("b1", BlockType.FORMULA, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 채점할 수 없는 문항은 오답률 신호를 만들지 못한다 — 집단 루프의 핵심 신호가 빠진다. */
        @Test
        @DisplayName("정답 없는 퀴즈는 만들 수 없다")
        void quizRequiresAnswer() {
            assertThatThrownBy(() -> new Block(
                            "b1", BlockType.QUIZ, null, Map.of("question", "질문", "choices", List.of("A", "B"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("answer");
        }

        @Test
        @DisplayName("도표는 spec 이 필요하다 — 이미지 바이너리가 아니라 명세를 든다")
        void figureRequiresSpec() {
            assertThatThrownBy(() -> new Block("b1", BlockType.FIGURE, null, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("spec");

            assertThat(Block.figure("b1", Map.of("type", "plot")).attributes()).containsKey("spec");
        }

        @Test
        @DisplayName("ID 형식이 틀리면 블록을 만들 수 없다")
        void rejectsMalformedId() {
            assertThatThrownBy(() -> Block.text("block-1", BlockType.PARAGRAPH, "본문"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(BlockIds.isValid("b12")).isTrue();
            assertThat(BlockIds.isValid("b")).isFalse();
            assertThat(BlockIds.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("내용 수정은 ID 를 유지한다 — 승계가 기본 동작이어야 한다")
        void withContentKeepsId() {
            Block revised = Block.text("b3", BlockType.PARAGRAPH, "원본").withContent("고친 본문", Map.of());

            assertThat(revised.id()).isEqualTo("b3");
            assertThat(revised.text()).isEqualTo("고친 본문");
        }

        @Test
        @DisplayName("순번 0 이하로는 ID 를 만들 수 없다")
        void rejectsNonPositiveSequence() {
            assertThatThrownBy(() -> BlockIds.of(0)).isInstanceOf(IllegalArgumentException.class);
            assertThat(BlockIds.of(12)).isEqualTo("b12");
            assertThat(BlockIds.sequenceOf("b12")).isEqualTo(12);
            assertThatThrownBy(() -> BlockIds.sequenceOf("nope")).isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * 공용 본문과 개인화 보충 블록의 ID 공간이 갈려 있어야 한다. 같은 공간을 쓰면 어떤 사용자의
         * 보충 블록에 붙은 신호가 공용 블록 신호와 섞이고, 그 오염은 에러 없이 집계를 틀리게 한다.
         */
        @Test
        @DisplayName("보충 블록 ID 는 다른 공간이다")
        void supplementIdsAreSeparateSpace() {
            assertThat(BlockIds.ofSupplement(3)).isEqualTo("s3");
            assertThat(BlockIds.isValid("s3")).isTrue();
            assertThat(BlockIds.isSupplement("s3")).isTrue();
            assertThat(BlockIds.isBody("s3")).isFalse();
            assertThat(BlockIds.isSupplement("b3")).isFalse();
            assertThat(BlockIds.isBody("b3")).isTrue();
            assertThat(BlockIds.sequenceOf("s3")).isEqualTo(3);
            assertThatThrownBy(() -> BlockIds.ofSupplement(0)).isInstanceOf(IllegalArgumentException.class);
        }

        /** 보충 블록도 블록이므로 만들 수는 있다 — 공용 문서에 들어가는 것만 막는다. */
        @Test
        @DisplayName("보충 ID 로 블록을 만들 수 있다")
        void supplementBlockIsAValidBlock() {
            assertThat(Block.text("s1", BlockType.PARAGRAPH, "추가 설명").id()).isEqualTo("s1");
        }
    }

    @Nested
    @DisplayName("챕터")
    class ChapterRules {

        @Test
        @DisplayName("생성 직후에는 본문이 없다 — current_version 0 이 그 상태를 표현한다")
        void createdWithoutBody() {
            Chapter chapter = Chapter.create(1L, "gradient-descent", "경사하강법", "요약", 0);

            assertThat(chapter.hasBody()).isFalse();
            assertThat(chapter.currentVersion()).isZero();
            assertThat(chapter.nextBlockSequence()).isEqualTo(1);
        }

        @Test
        @DisplayName("버전은 증가만 한다")
        void versionOnlyIncreases() {
            Chapter chapter =
                    Chapter.create(1L, "gradient-descent", "경사하강법", null, 0).withNewVersion(1, 6);

            assertThat(chapter.currentVersion()).isEqualTo(1);
            assertThat(chapter.hasBody()).isTrue();
            assertThatThrownBy(() -> chapter.withNewVersion(1, 7)).isInstanceOf(IllegalArgumentException.class);
        }

        /** 카운터가 작아지면 다음 발급이 이미 쓰인 ID 를 다시 내준다. */
        @Test
        @DisplayName("블록 ID 카운터는 되돌아가지 않는다")
        void counterNeverDecreases() {
            Chapter chapter = Chapter.create(1L, "gradient-descent", "경사하강법", null, 0)
                    .withNewVersion(1, 9)
                    .withNewVersion(2, 3);

            assertThat(chapter.blockIdSequence()).isEqualTo(9);
            assertThat(chapter.nextBlockSequence()).isEqualTo(10);
        }

        @Test
        @DisplayName("개요 수정은 버전과 카운터를 건드리지 않는다")
        void outlineUpdateKeepsVersioning() {
            Chapter chapter = Chapter.create(1L, "gradient-descent", "경사하강법", null, 0)
                    .withNewVersion(1, 4)
                    .withOutline("경사하강법 개론", "새 요약");

            assertThat(chapter.title()).isEqualTo("경사하강법 개론");
            assertThat(chapter.currentVersion()).isEqualTo(1);
            assertThat(chapter.blockIdSequence()).isEqualTo(4);
        }

        @Test
        @DisplayName("뼈대 · 키 · 제목은 필수다")
        void requiresIdentity() {
            assertThatThrownBy(() -> Chapter.create(null, "key", "제목", null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Chapter.create(1L, "  ", "제목", null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Chapter.create(1L, "key", " ", null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("최초 생성 버전만 수정이 아니다 — 배지 노출 판정의 기준")
        void revisionFlag() {
            BlockDocument body = BlockDocument.of(Block.text("b1", BlockType.PARAGRAPH, "본문"));

            assertThat(new ChapterVersion(null, 1L, 1, body, null, ChapterVersionSource.GENERATED, null, null)
                            .isRevision())
                    .isFalse();
            assertThat(new ChapterVersion(
                                    null, 1L, 2, body, "설명 보강", ChapterVersionSource.REVISED_BY_SIGNAL, null, null)
                            .isRevision())
                    .isTrue();
        }

        @Test
        @DisplayName("버전은 1 이상이고 본문과 출처가 필수다")
        void versionInvariants() {
            BlockDocument body = BlockDocument.of(Block.text("b1", BlockType.PARAGRAPH, "본문"));

            assertThatThrownBy(() ->
                            new ChapterVersion(null, 1L, 0, body, null, ChapterVersionSource.GENERATED, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                            new ChapterVersion(null, 1L, 1, null, null, ChapterVersionSource.GENERATED, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ChapterVersion(null, 1L, 1, body, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                            new ChapterVersion(null, null, 1, body, null, ChapterVersionSource.GENERATED, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
