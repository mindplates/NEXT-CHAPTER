package com.mindplates.nextchapter.application.generation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.signal.model.FormatBreakdown;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 집단 루프 수정안 프롬프트와 근거 파싱.
 *
 * <p>블록 파싱 자체는 {@link ChapterBodyPrompts#parse(String)} 을 그대로 쓴다 — 응답 형식(블록 배열)이
 * 같고, 두 경로가 다르게 파싱하면 같은 챕터인데 경로에 따라 본문 해석이 달라질 수 있다.
 */
final class ChapterRevisionPrompts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * <b>기존 블록 ID 를 유지하라고 명시한다.</b> 여기서 지시가 빠지면 모델이 전 블록을 새로 쓰고, 그러면
     * ID 가 전부 갈려 수정 전후 신호를 이어 볼 수 없다 — 3개월 성공 기준 2번이 깨진다.
     */
    static final String SYSTEM_PROMPT =
            """
            너는 교육 콘텐츠 편집자다. 이미 발행된 챕터의 본문을 학습자 신호에 따라 고친다.

            블록 타입:
            - heading   : 소제목. text 에 제목
            - paragraph : 본문 문단. text 에 내용
            - formula   : 수식. text 에 LaTeX 소스
            - figure    : 도표. attributes.spec 에 렌더 명세(JSON)
            - narration : 영상 음성·자막의 원천 대본
            - quiz      : attributes 에 question, choices(배열), answer(정답 선택지의 0부터 시작하는 번호)

            반드시 지킬 것:
            - "지목된 블록"이 학습자가 막힌 지점이다. 그 블록의 설명·예시를 더 명확하게 고쳐라. 그
              블록이 다루는 개념 자체를 바꾸지 마라 — 범위가 넓어지면 다른 챕터와 겹친다.
            - 바뀌지 않는 블록은 id 를 그대로 돌려줘라. id 를 생략하거나 다른 값을 주면 그 블록에 쌓인
              이전 신호와 연결이 끊겨 수정 전후 비교가 불가능해진다.
            - 지목된 블록을 고치는 과정에서 전후 문맥이 어색해지면 인접 블록도 함께 다듬어라. 그때도
              내용이 유지되는 블록은 기존 id 를 그대로 써라.
            - 핵심 사실 주장에는 attributes.sources 를 유지하거나 새로 달아라. 응답 전체에서 최소 한
              블록에는 반드시 있어야 한다.
            - 퀴즈 오답이 몰렸다면 문항이나 선택지를 더 명확히 구분되게 고쳐라. attributes.difficulty
              가 있다면 유지해라 — 난이도를 바꾸면 다른 사용자에게 다른 문항이 나가는 근거가 흔들린다.
            - 존재하지 않던 사실을 새로 만들지 마라.
            - "rationale" 필드에 무엇을 어떤 근거로 고쳤는지 2~3문장으로 써라. 이 문장이 승인 대기열에
              그대로 노출된다.

            응답은 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스를 붙이지 않는다.
            """;

    private ChapterRevisionPrompts() {}

    static String prompt(
            String chapterTitle,
            List<Block> existingBlocks,
            String targetBlockId,
            long questionCount,
            long attemptCount,
            long wrongCount,
            List<FormatBreakdown> formatBreakdown) {
        double wrongRatePercent = attemptCount == 0 ? 0.0 : (double) wrongCount / attemptCount * 100;
        return """
                챕터: %s

                지목된 블록: %s
                집계된 신호 — 질문 %d건, 시도 %d회, 오답 %d회 (오답률 %.0f%%)
                형태별 분해:
                %s

                현재 본문 (JSON, 유지할 블록은 같은 id 를 그대로 돌려줘라):
                %s

                응답 형식:
                {
                  "rationale": "이 블록의 학습률 설명이 선행 개념 없이 등장해 혼동을 준다. 예시를 추가하고
                   b3 앞에 정의를 보강했다.",
                  "blocks": [
                    {"id": "b1", "type": "heading", "text": "..."},
                    {"id": "b2", "type": "paragraph", "text": "...",
                     "attributes": {"sources": [{"title": "…", "url": "https://…", "quote": "…"}]}}
                  ]
                }
                """
                .formatted(
                        chapterTitle,
                        targetBlockId,
                        questionCount,
                        attemptCount,
                        wrongCount,
                        wrongRatePercent,
                        formatBreakdownLines(formatBreakdown),
                        existingBlocksJson(existingBlocks));
    }

    /** 응답에서 근거 문장만 뽑는다. 블록 파싱과 별개 경로다 — 근거가 없어도 블록은 살릴 수 있어야 한다. */
    static String rationale(String response) {
        JsonNode root = ChapterBodyPrompts.readObject(response);
        String rationale = root.path("rationale").asText(null);
        if (rationale == null || rationale.isBlank()) {
            throw new ExternalApiException("수정안 응답에 rationale 이 없습니다.");
        }
        return rationale;
    }

    private static String formatBreakdownLines(List<FormatBreakdown> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) {
            return "- (형태별 데이터 없음)";
        }
        return breakdown.stream()
                .map(entry -> "- %s: 시도 %d회, 오답 %d회 (%.0f%%)"
                        .formatted(entry.format(), entry.attemptCount(), entry.wrongCount(), entry.wrongRate() * 100))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- (형태별 데이터 없음)");
    }

    /** 모델이 id 를 그대로 읽고 돌려줄 수 있게 존재하는 블록을 JSON 배열로 그대로 넘긴다. */
    private static String existingBlocksJson(List<Block> existingBlocks) {
        List<Map<String, Object>> serialized = existingBlocks.stream()
                .map(block -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", block.id());
                    node.put("type", block.type().name().toLowerCase(java.util.Locale.ROOT));
                    node.put("text", block.text());
                    node.put("attributes", block.attributes());
                    return node;
                })
                .toList();
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(serialized);
        } catch (Exception e) {
            throw new ExternalApiException(
                    "현재 본문을 프롬프트로 직렬화하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
