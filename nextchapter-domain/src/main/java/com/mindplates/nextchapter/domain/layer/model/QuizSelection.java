package com.mindplates.nextchapter.domain.layer.model;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.QuizDifficulty;

/**
 * 이해도에 맞는 문항을 고른다.
 *
 * <p><b>문항을 만들지 않고 고른다.</b> 사용자마다 문항을 새로 생성하면 같은 지점의 오답률을 겹쳐 볼 수 없고
 * 생성 비용이 사용자 수만큼 늘어난다. 문항은 공용이고, 누구에게 보이는지만 달라진다.
 *
 * <p>{@link QuizDifficulty#CORE} 는 <b>누구에게나</b> 보인다. 그래서 이해도를 모르는 사용자도 풀 문항이
 * 있고, 모든 문항이 최소 한 집단에서는 비교 대상이 된다 — 어느 문항도 아무도 보지 않는 상태가 되지 않는다.
 */
public final class QuizSelection {

    private QuizSelection() {}

    /**
     * 이 블록을 이 사용자에게 보여 줄지.
     *
     * <p>퀴즈가 아닌 블록은 <b>항상</b> 보인다 — 설명 본문은 누구에게나 같아야 한다.
     */
    public static boolean includes(UnderstandingLevel level, Block block) {
        if (block.type() != BlockType.QUIZ) {
            return true;
        }
        return includes(level, QuizDifficulty.of(block));
    }

    /**
     * 막힌 사용자에게 어려운 문항을 내밀면 오답이 쌓여 더 막힌 것으로 판정된다 — 이해도 판정이 자기
     * 자신을 강화하는 고리가 생긴다. 반대로 충분히 이해한 사용자에게 쉬운 문항만 내밀면 이해도가 더
     * 오를 근거가 나오지 않는다.
     */
    public static boolean includes(UnderstandingLevel level, QuizDifficulty difficulty) {
        if (difficulty == QuizDifficulty.CORE) {
            return true;
        }
        return switch (level) {
            case STRUGGLING -> difficulty == QuizDifficulty.EASY;
            case CONFIDENT -> difficulty == QuizDifficulty.HARD;
                // 이해도를 모르거나 배우는 중이면 기본 문항만 낸다. 쉬운 문항으로 시작하면 이해도가
                // 실제보다 높게 나오고, 그 값이 다음 챕터 선택을 흔든다.
            case UNKNOWN, LEARNING -> false;
        };
    }
}
