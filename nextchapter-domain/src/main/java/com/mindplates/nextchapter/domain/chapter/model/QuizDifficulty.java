package com.mindplates.nextchapter.domain.chapter.model;

import java.util.Locale;

/**
 * 퀴즈 문항의 난이도. 생성 시 문항에 붙고, 개인화는 <b>고를 때</b> 쓴다.
 *
 * <p>블록의 속성으로 두는 이유는 난이도가 <b>공용 사실</b>이기 때문이다. 사용자마다 문항을 다시 만들면
 * 같은 지점의 오답률을 겹쳐 볼 수 없다 — 문항은 공용이고 누구에게 보이는지만 달라진다.
 *
 * <p>속성이 없는 문항은 {@link #CORE} 다. 기존 문항에 난이도를 뒤늦게 붙일 수 있어야 하고, 없는 것을
 * "쉬움"이나 "어려움"으로 취급하면 그 문항이 일부 사용자에게서 조용히 사라진다.
 */
public enum QuizDifficulty {
    EASY,

    /** 기본 문항. 누구에게나 보인다. */
    CORE,

    HARD;

    /** 블록 속성 키. */
    public static final String ATTRIBUTE = "difficulty";

    /** 모르는 값도 {@link #CORE} 로 접는다 — 문항이 사라지는 것보다 낫다. */
    public static QuizDifficulty of(Object raw) {
        if (raw == null) {
            return CORE;
        }
        try {
            return valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CORE;
        }
    }

    public static QuizDifficulty of(Block block) {
        return of(block.attributes().get(ATTRIBUTE));
    }
}
