package com.mindplates.nextchapter.application.layer.service;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 보충 블록 생성 프롬프트.
 *
 * <p><b>본문을 다시 쓰지 말라고 명시한다.</b> 모델에게 "이 문단을 개선하라"고 하면 개선된 문단이 돌아오고,
 * 그것을 삽입하면 사용자는 같은 내용을 두 번 읽는다. 구조로도 막혀 있지만(삽입만 가능하다) 프롬프트가
 * 어긋나면 쓸모없는 블록이 만들어진다.
 *
 * <p>그 사용자의 <b>질문과 오답</b>을 함께 넣는다. 무엇이 막혔는지가 프롬프트에 없으면 "일반적인 추가
 * 설명"이 나오고, 그건 이미 본문에 있는 이야기를 반복하는 것으로 끝난다.
 */
final class SupplementPrompts {

    /** 보충 블록은 문단 하나 분량이다. 길면 본문을 대체하기 시작한다. */
    static final int MAX_TOKENS = 1024;

    private static final String SYSTEM =
            """
            당신은 학습자가 막힌 지점을 풀어 주는 조교입니다.
            규칙:
            1. 본문을 다시 쓰지 마세요. 본문은 그대로 남고, 당신의 설명이 그 뒤에 덧붙습니다.
            2. 이미 본문에 있는 설명을 반복하지 마세요. 다른 각도(구체적 예시, 비유, 반례, 계산 과정)로 접근하세요.
            3. 학습자가 실제로 막힌 지점에만 답하세요. 새 주제를 꺼내지 마세요.
            4. 한 문단, 200자 안팎으로 쓰세요. 길면 본문을 대체하게 됩니다.
            5. 마크다운·머리말·인사말 없이 설명 본문만 출력하세요.
            """;

    private SupplementPrompts() {}

    static String system() {
        return SYSTEM;
    }

    static String user(String chapterTitle, List<Block> chapterBlocks, Block anchor, List<Signal> signals) {
        return """
                # 챕터
                %s

                # 챕터 본문 (참고 — 이 내용을 반복하지 마세요)
                %s

                # 학습자가 막힌 지점 (이 블록 뒤에 당신의 설명이 들어갑니다)
                [%s] %s

                # 이 학습자가 이 챕터에서 남긴 신호
                %s

                위 지점을 다른 각도로 풀어 주는 설명 한 문단을 쓰세요."""
                .formatted(chapterTitle, body(chapterBlocks), anchor.id(), blockText(anchor), signalSummary(signals));
    }

    private static String body(List<Block> blocks) {
        return blocks.stream()
                .map(block -> "[" + block.id() + " " + block.type() + "] " + blockText(block))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 텍스트가 없는 블록(도표·퀴즈)은 속성을 그대로 넣는다. 비워 두면 모델이 그 지점에 무엇이 있는지 모르는
     * 채로 설명을 쓰게 된다.
     */
    private static String blockText(Block block) {
        return block.text() != null ? block.text() : String.valueOf(block.attributes());
    }

    /**
     * 질문은 본문 그대로, 오답은 사실만 넣는다. 오답의 선택지를 넣으면 모델이 "그 선택지가 왜 틀렸는지"에만
     * 답하는 경향이 있고, 막힌 지점은 그보다 앞에 있다.
     */
    private static String signalSummary(List<Signal> signals) {
        if (signals.isEmpty()) {
            return "(없음 — 학습자가 이 지점을 다시 보고 싶다고만 했습니다)";
        }
        return signals.stream()
                .map(SupplementPrompts::describe)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private static String describe(Signal signal) {
        if (signal.type() == SignalType.QUESTION || signal.type() == SignalType.ERROR_REPORT) {
            return "- [" + signal.blockId() + "] " + signal.type() + ": "
                    + signal.payload().get("text");
        }
        if (signal.type() == SignalType.QUIZ_ANSWER
                && Boolean.FALSE.equals(signal.payload().get("correct"))) {
            return "- [" + signal.blockId() + "] 퀴즈를 틀렸습니다";
        }
        return null;
    }
}
