package com.mindplates.nextchapter.application.generation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ③ 본문 생성 프롬프트와 응답 파싱. */
final class ChapterBodyPrompts {

    private static final Logger log = LoggerFactory.getLogger(ChapterBodyPrompts.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 블록 타입과 {@code narration} 의 역할을 지시에 넣는다. 넣지 않으면 모델은 마크다운 한 덩어리를
     * 내놓고, 그러면 블록 단위 신호(어느 문단에서 막혔는가)를 붙일 지점이 사라진다 — 챕터 단위 신호로는
     * "이 챕터가 어렵다"까지만 알 수 있다.
     *
     * <p>출처를 "핵심 주장"에만 달라고 하는 이유는 전부에 달게 하면 모델이 형식을 채우려고 아무 문서나
     * 붙이기 때문이다. 그 출처는 검증 패스를 통과하지 못하면서 검증 비용만 늘린다.
     */
    static final String SYSTEM_PROMPT =
            """
            너는 교육 콘텐츠 작성자다. 챕터 하나의 본문을 블록 문서로 쓴다.

            블록 타입:
            - heading   : 소제목. text 에 제목
            - paragraph : 본문 문단. text 에 내용
            - formula   : 수식. text 에 LaTeX 소스
            - figure    : 도표. attributes.spec 에 렌더 명세(JSON). 이미지 URL 을 쓰지 마라
            - narration : 영상 음성·자막의 원천 대본. 웹 화면에는 보이지 않는다
            - quiz      : attributes 에 question, choices(배열), answer(정답 선택지의 0부터 시작하는 번호)

            반드시 지킬 것:
            - 개요의 항목을 빠짐없이 다뤄라. 개요에 없는 내용을 끌어오지 마라.
            - 선행 챕터가 다루는 내용은 이미 아는 것으로 두고 다시 설명하지 마라.
            - 다른 챕터가 다룰 내용을 끌어오지 마라.
            - 핵심 사실 주장에는 attributes.sources 에 출처를 달아라. 최소 한 블록에는 반드시 있어야 한다.
              형식: [{"title": "문서 제목", "url": "https://…", "quote": "근거가 되는 문장"}]
              추측으로 URL 을 만들지 마라. 확실하지 않으면 url 을 빼고 title 과 quote 만 쓴다.
            - narration 블록을 최소 하나 포함하라. 영상 렌더링이 이 블록만을 원천으로 쓴다.
            - quiz 블록을 최소 하나 포함하라. 오답률이 집단 루프의 핵심 신호다.

            응답은 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스를 붙이지 않는다.
            """;

    private ChapterBodyPrompts() {}

    static String prompt(
            String topicName,
            String chapterTitle,
            String chapterSummary,
            List<String> outlinePoints,
            List<String> prerequisiteTitles,
            List<String> siblingTitles) {
        return """
                주제: %s
                이 챕터: %s
                %s

                이 챕터가 다뤄야 할 항목:
                %s

                이미 다뤄진 선행 챕터 (다시 설명하지 마라):
                %s

                같은 뼈대의 다른 챕터 (이들의 내용을 끌어오지 마라):
                %s

                응답 형식:
                {
                  "blocks": [
                    {"type": "heading", "text": "경사하강법이란"},
                    {"type": "paragraph", "text": "손실함수를 …",
                     "attributes": {"sources": [{"title": "…", "url": "https://…", "quote": "…"}]}},
                    {"type": "formula", "text": "\\\\theta := \\\\theta - \\\\alpha \\\\nabla J"},
                    {"type": "figure", "attributes": {"spec": {"kind": "plot", "x": "step", "y": "loss"}}},
                    {"type": "narration", "text": "이제 학습률을 봅시다"},
                    {"type": "quiz", "attributes": {"question": "학습률이 크면?",
                     "choices": ["발산한다", "수렴한다"], "answer": 0}}
                  ]
                }
                """
                .formatted(
                        topicName,
                        chapterTitle,
                        chapterSummary == null ? "" : "요약: " + chapterSummary,
                        bullets(outlinePoints),
                        bullets(prerequisiteTitles),
                        bullets(siblingTitles));
    }

    /**
     * 블록 제안으로 파싱한다. ID 는 읽되 <b>검증하지 않는다</b> — 확정은 승계 규칙의 일이다.
     *
     * <p>알 수 없는 타입이나 필수 항목이 빠진 블록은 건너뛴다. 블록 하나 때문에 챕터 전체를 버리는 것은
     * 과하고, 남은 블록으로도 본문은 성립한다. 다만 <b>전부 건너뛰어 비게 되면</b> 실패로 올린다.
     */
    static List<ProposedBlock> parse(String response) {
        JsonNode root = readObject(response);
        List<ProposedBlock> blocks = new ArrayList<>();

        for (JsonNode node : root.path("blocks")) {
            BlockType type = blockType(node.path("type").asText(null));
            if (type == null) {
                log.warn("[본문] 알 수 없는 블록 타입을 건너뛴다: {}", node.path("type").asText(null));
                continue;
            }
            try {
                ProposedBlock proposed = new ProposedBlock(
                        node.path("id").asText(null),
                        type,
                        node.path("text").asText(null),
                        attributes(node.path("attributes")));
                // 타입별 필수 항목 검증은 Block 이 한다. 임시 ID 로 한 번 만들어 보고 통과하는 것만 남긴다 —
                // 여기서 걸러내지 않으면 정답 없는 퀴즈가 기록 시점까지 살아남아 챕터 전체를 실패시킨다.
                proposed.toBlock(BlockIds.of(1));
                blocks.add(proposed);
            } catch (RuntimeException e) {
                log.warn("[본문] 블록을 건너뛴다 type={} 사유={}", type, e.getMessage());
            }
        }
        if (blocks.isEmpty()) {
            throw new ExternalApiException("본문 생성 응답에 쓸 수 있는 블록이 없습니다.");
        }
        return blocks;
    }

    private static Map<String, Object> attributes(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        // properties() 를 쓰는 이유는 fields() 가 deprecated 이기 때문이다 (Jackson 2.19+).
        node.properties()
                .forEach(entry -> attributes.put(entry.getKey(), MAPPER.convertValue(entry.getValue(), Object.class)));
        return attributes;
    }

    private static BlockType blockType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return BlockType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String bullets(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- (없음)";
        }
        return items.stream()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- (없음)");
    }

    /** 패키지 안의 다른 프롬프트 빌더(수정안 생성)도 같은 JSON 추출 규칙을 쓴다. */
    static JsonNode readObject(String response) {
        if (response == null || response.isBlank()) {
            throw new ExternalApiException("본문 생성 응답이 비어 있습니다.");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ExternalApiException("본문 생성 응답에서 JSON 객체를 찾을 수 없습니다.");
        }
        try {
            return MAPPER.readTree(response.substring(start, end + 1));
        } catch (Exception e) {
            throw new ExternalApiException(
                    "본문 생성 응답을 파싱하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    /** 출처가 붙은 블록이 하나라도 있는지. {@link Block#SOURCES} 속성 기준이다. */
    static boolean hasAnySource(List<ProposedBlock> blocks) {
        return blocks.stream().anyMatch(block -> {
            Object sources = block.attributes().get(Block.SOURCES);
            return sources instanceof java.util.Collection<?> collection && !collection.isEmpty();
        });
    }
}
