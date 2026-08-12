package com.mindplates.nextchapter.domain.layer.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.chapter.model.QuizDifficulty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>완료 기준 3번</b> — 같은 챕터라도 사용자별로 퀴즈 난이도가 다르다.
 *
 * <p>여기서 지켜야 하는 것은 두 가지다. 문항 <b>선택</b>만 달라지고 설명 본문은 누구에게나 같아야 하며,
 * 어느 문항도 아무도 보지 않는 상태가 되어서는 안 된다(그러면 그 문항의 오답률이 영원히 비어 있다).
 */
@DisplayName("퀴즈 문항 선택")
class QuizSelectionTest {

    private static Block quiz(String id, QuizDifficulty difficulty) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("question", "질문");
        attributes.put("choices", List.of("가", "나"));
        attributes.put("answer", "가");
        if (difficulty != null) {
            attributes.put(QuizDifficulty.ATTRIBUTE, difficulty.name());
        }
        return new Block(id, BlockType.QUIZ, null, attributes);
    }

    private static BlockDocument document() {
        return BlockDocument.of(
                Block.text("b1", BlockType.PARAGRAPH, "본문"),
                quiz("b2", QuizDifficulty.EASY),
                quiz("b3", QuizDifficulty.CORE),
                quiz("b4", QuizDifficulty.HARD),
                quiz("b5", null));
    }

    private static List<String> visible(UnderstandingLevel level) {
        return LayerComposition.compose(document(), DeliveryFormat.WEB, null, level).stream()
                .map(Block::id)
                .toList();
    }

    /** 막힌 사용자에게 어려운 문항을 내밀면 오답이 쌓여 더 막힌 것으로 판정된다 — 판정이 자기를 강화한다. */
    @Test
    @DisplayName("막힌 사용자는 쉬운 문항과 기본 문항을 본다")
    void strugglingSeesEasy() {
        assertThat(visible(UnderstandingLevel.STRUGGLING)).containsExactly("b1", "b2", "b3", "b5");
    }

    @Test
    @DisplayName("충분히 이해한 사용자는 어려운 문항과 기본 문항을 본다")
    void confidentSeesHard() {
        assertThat(visible(UnderstandingLevel.CONFIDENT)).containsExactly("b1", "b3", "b4", "b5");
    }

    /** 쉬운 문항으로 시작하면 이해도가 실제보다 높게 나오고, 그 값이 다음 챕터 선택을 흔든다. */
    @Test
    @DisplayName("이해도를 모르면 기본 문항만 본다")
    void unknownSeesCoreOnly() {
        assertThat(visible(UnderstandingLevel.UNKNOWN)).containsExactly("b1", "b3", "b5");
        assertThat(visible(UnderstandingLevel.LEARNING)).containsExactly("b1", "b3", "b5");
    }

    /** 같은 챕터, 다른 이해도 → 다른 문항. 그것이 완료 기준 3번이다. */
    @Test
    @DisplayName("이해도가 다르면 보이는 문항이 다르다")
    void differentLevelsSeeDifferentQuizzes() {
        assertThat(visible(UnderstandingLevel.STRUGGLING)).isNotEqualTo(visible(UnderstandingLevel.CONFIDENT));
    }

    /**
     * 설명 본문이 사용자마다 달라지는 순간 "같은 지점에서 틀렸다"를 비교할 수 없다. 난이도 개인화가 본문을
     * 건드리지 않는다는 것을 값으로 고정한다.
     */
    @Test
    @DisplayName("설명 본문은 이해도와 무관하게 그대로다")
    void bodyBlocksAreUnaffected() {
        for (UnderstandingLevel level : UnderstandingLevel.values()) {
            assertThat(visible(level)).contains("b1");
        }
    }

    /** 난이도가 없는 문항이 일부 사용자에게서 사라지면 그 문항의 오답률이 조용히 비어 간다. */
    @Test
    @DisplayName("난이도가 없거나 모르는 값이면 기본 문항이다")
    void missingDifficultyIsCore() {
        assertThat(QuizDifficulty.of((Object) null)).isEqualTo(QuizDifficulty.CORE);
        assertThat(QuizDifficulty.of("wat")).isEqualTo(QuizDifficulty.CORE);
        assertThat(QuizDifficulty.of("hard")).isEqualTo(QuizDifficulty.HARD);
        for (UnderstandingLevel level : UnderstandingLevel.values()) {
            assertThat(visible(level)).contains("b5");
        }
    }

    @Test
    @DisplayName("퀴즈가 아닌 블록은 언제나 보인다")
    void nonQuizAlwaysIncluded() {
        assertThat(QuizSelection.includes(UnderstandingLevel.STRUGGLING, Block.text("b1", BlockType.PARAGRAPH, "본문")))
                .isTrue();
    }

    /** 이해도를 주지 않는 호출은 기본 문항만 보여 준다 — 개인화를 모르는 경로가 어려운 문항을 내밀지 않는다. */
    @Test
    @DisplayName("이해도 없는 합성은 UNKNOWN 과 같다")
    void defaultOverloadMatchesUnknown() {
        assertThat(LayerComposition.compose(document(), DeliveryFormat.WEB, null).stream()
                        .map(Block::id)
                        .toList())
                .isEqualTo(visible(UnderstandingLevel.UNKNOWN));
    }
}
