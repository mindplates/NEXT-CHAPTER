package com.mindplates.nextchapter.application.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 단계적 매핑 프롬프트와 응답 파싱.
 *
 * <p>계층마다 별도 호출을 하는 이유가 "층별 좁히기"다. 한 번에 주제를 고르게 하면 후보 목록이 전체
 * 주제 수만큼 커져 프롬프트에 들어가지 않고, 후보가 많을수록 오매칭이 늘어난다. 층을 나누면 각 단계의
 * 후보가 수십 개로 유지된다.
 *
 * <p>대가는 호출이 세 번이라는 점이다. 감수하는 이유는 오매칭의 대가가 훨씬 크기 때문이다 — 잘못
 * 매핑된 입력은 신호를 엉뚱한 뼈대에 붙이고, 그건 에러 없이 개선 루프의 전제를 무너뜨린다.
 */
final class TopicMappingPrompts {

    private static final Logger log = LoggerFactory.getLogger(TopicMappingPrompts.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 확신이 없으면 null 을 고르라고 명시한다. 억지로 하나를 고르게 하면 관련 없는 입력이 가장
     * 가까운 주제로 흘러들어가고, 그 오매칭은 사용자에게 보이지도 않는다.
     */
    static final String SYSTEM_PROMPT =
            """
            너는 교육 콘텐츠 카탈로그의 분류기다. 주어진 목록에서만 고르고, 목록에 없는 것을 만들지 않는다.
            적합한 항목이 없거나 확신이 없으면 반드시 null 을 골라라 — 억지로 가장 가까운 것을 고르는 것보다
            null 이 낫다. 잘못된 분류는 이후 모든 학습 신호를 엉뚱한 곳에 붙인다.
            응답은 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스를 붙이지 않는다.
            """;

    private TopicMappingPrompts() {}

    static String domainPrompt(List<CatalogDomain> domains, String input) {
        return """
                사용자 입력: "%s"

                아래 도메인 중 이 입력이 속하는 것을 하나 고르라.

                %s

                응답 형식: {"slug": "고른-slug" 또는 null}
                """
                .formatted(
                        input,
                        bulletList(domains, CatalogDomain::slug, CatalogDomain::name, CatalogDomain::description));
    }

    static String fieldPrompt(CatalogDomain domain, List<CatalogField> fields, String input) {
        return """
                사용자 입력: "%s"
                이미 결정된 도메인: %s

                아래 분야 중 이 입력이 속하는 것을 하나 고르라.

                %s

                응답 형식: {"slug": "고른-slug" 또는 null}
                """
                .formatted(
                        input,
                        domain.name(),
                        bulletList(fields, CatalogField::slug, CatalogField::name, CatalogField::description));
    }

    /**
     * 마지막 단계만 신규 주제 제안을 함께 받는다. 어차피 "맞는 주제가 없다"는 판정을 내리는 호출이므로
     * 같은 자리에서 이름과 slug 를 받아 두면 거절 경로가 호출 하나를 아낀다.
     */
    static String topicPrompt(CatalogField field, List<CatalogTopic> topics, String input) {
        return """
                사용자 입력: "%s"
                이미 결정된 분야: %s

                아래 주제 중 이 입력이 가리키는 것을 하나 고르라. 같은 대상을 다른 말로 표현한 것이면 고르고,
                범위가 다르거나 더 좁은/넓은 대상이면 null 을 골라라.

                %s

                맞는 주제가 없어 null 을 고를 때는 이 입력에 어울리는 새 주제의 이름과 slug 를 함께 제안하라.
                slug 는 소문자 영문·숫자·하이픈만 쓴다.

                응답 형식: {"slug": "고른-slug" 또는 null, "proposedName": "새 주제 이름" 또는 null,
                          "proposedSlug": "new-topic-slug" 또는 null}
                """
                .formatted(
                        input,
                        field.name(),
                        bulletList(topics, CatalogTopic::slug, CatalogTopic::name, CatalogTopic::description));
    }

    /** 고른 slug. 응답이 깨졌거나 null 이면 empty — 호출자는 이것을 "매칭 실패"로 다룬다. */
    static Optional<String> pickedSlug(String response) {
        return parse(response)
                .map(node -> node.path("slug"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    static Optional<String> proposedName(String response) {
        return textField(response, "proposedName");
    }

    static Optional<String> proposedSlug(String response) {
        return textField(response, "proposedSlug");
    }

    private static Optional<String> textField(String response, String field) {
        return parse(response)
                .map(node -> node.path(field))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }

    /**
     * 코드펜스를 벗기고 첫 JSON 객체만 읽는다. 형식을 지키라고 지시해도 모델은 종종 설명을 덧붙이므로,
     * 파싱 실패를 예외가 아니라 "매칭 실패"로 다룬다 — 그래야 사용자가 후보 제시 경로로 흘러가고,
     * 매핑 실패가 요청 실패로 번지지 않는다.
     */
    private static Optional<JsonNode> parse(String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        String text = response.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("[매핑] JSON 객체를 찾을 수 없는 응답: {}", abbreviate(text));
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readTree(text.substring(start, end + 1)));
        } catch (Exception e) {
            log.warn("[매핑] 응답 파싱 실패: {}", abbreviate(text));
            return Optional.empty();
        }
    }

    private static <T> String bulletList(
            List<T> items, Function<T, String> slug, Function<T, String> name, Function<T, String> description) {
        return items.stream()
                .map(item -> {
                    String desc = description.apply(item);
                    return "- " + slug.apply(item) + " : " + name.apply(item) + (desc == null ? "" : " — " + desc);
                })
                .collect(Collectors.joining("\n"));
    }

    private static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
