package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.catalog.port.in.command.SeedCatalogCommand;
import com.mindplates.nextchapter.domain.catalog.model.CatalogSlugs;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시드 파일 자체를 검사한다.
 *
 * <p>런타임에서 이 파일의 파싱 실패는 <b>경고 로그 한 줄</b>로 끝난다 — 기동을 막지 않기로 했기 때문이다.
 * 그 선택의 대가가 "시드가 조용히 투입되지 않는 상태"이고, 그래서 파일 모양을 테스트로 고정한다.
 *
 * <p>동시에 <b>규모</b>도 확인한다. 초기 카탈로그는 소수 도메인을 깊게 채우는 것이고, 주제를 넓게 벌리면
 * 뼈대당 사용자가 1~2명씩 붙어 집단 루프가 임계치를 영원히 못 넘는다 — 파일을 늘리다 그 선을 넘는 것을
 * 여기서 잡는다. 전량 30~50개는 M7 P7.2 다.
 */
@DisplayName("초기 카탈로그 시드 파일")
class CatalogSeedFileTest {

    private static final String LOCATION = "/catalog/seed.json";

    private static SeedCatalogCommand load() throws Exception {
        // Boot 의 ObjectMapper 는 모르는 속성을 무시한다. 파일의 _comment 가 그것에 의존한다.
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream in = CatalogSeedFileTest.class.getResourceAsStream(LOCATION)) {
            assertThat(in).as("시드 파일이 클래스패스에 있어야 한다").isNotNull();
            return mapper.readValue(in, SeedCatalogCommand.class);
        }
    }

    @Test
    @DisplayName("커맨드로 그대로 읽힌다")
    void parses() throws Exception {
        SeedCatalogCommand seed = load();

        assertThat(seed.domains()).isNotEmpty();
        assertThat(seed.domains().getFirst().fields()).isNotEmpty();
        assertThat(topics(seed)).isNotEmpty();
    }

    /** 초기 카탈로그는 소수 도메인을 깊게 — 도메인 1~2개, 분야 5~10개, 주제 3~5개. */
    @Test
    @DisplayName("규모가 초기 카탈로그 범위 안이다")
    void staysSmall() throws Exception {
        SeedCatalogCommand seed = load();

        assertThat(seed.domains()).hasSizeBetween(1, 2);
        assertThat(seed.domains().stream()
                        .mapToInt(domain -> domain.fields().size())
                        .sum())
                .isBetween(5, 10);
        assertThat(topics(seed)).hasSizeBetween(3, 5);
    }

    /** 슬러그가 도메인 규칙을 통과해야 한다 — 통과하지 못하면 그 층부터 시드가 멈춘다. */
    @Test
    @DisplayName("모든 슬러그가 규칙을 통과한다")
    void slugsAreValid() throws Exception {
        SeedCatalogCommand seed = load();

        seed.domains().forEach(domain -> {
            assertThat(CatalogSlugs.require(domain.slug())).isEqualTo(domain.slug());
            domain.fields().forEach(field -> {
                assertThat(CatalogSlugs.require(field.slug())).isEqualTo(field.slug());
                field.topics().forEach(topic -> assertThat(CatalogSlugs.require(topic.slug()))
                        .isEqualTo(topic.slug()));
            });
        });
    }

    /**
     * 별칭이 없는 주제는 첫 관문(별칭 정확 일치)을 쓸 수 없어 입력마다 LLM 을 부른다 — 가장 싼 수렴 수단을
     * 비워 두는 것이다.
     */
    @Test
    @DisplayName("모든 주제에 별칭이 있다")
    void everyTopicHasAliases() throws Exception {
        assertThat(topics(load()))
                .allSatisfy(topic -> assertThat(topic.aliases()).isNotEmpty());
    }

    /**
     * 같은 표현이 두 주제를 가리키면 그 입력은 어느 뼈대로 갈지 알 수 없다. 런타임에는 UNIQUE 가 막지만,
     * 시드 파일 안의 중복은 <b>조용히 건너뛰어져</b> 의도한 별칭이 붙지 않는 상태로 끝난다.
     */
    @Test
    @DisplayName("별칭이 파일 안에서 중복되지 않는다")
    void aliasesAreUniqueWithinFile() throws Exception {
        List<String> normalized = topics(load()).stream()
                .flatMap(topic -> topic.aliases().stream())
                .map(com.mindplates.nextchapter.common.text.Strings::normalizeForMatch)
                .toList();

        assertThat(normalized).doesNotHaveDuplicates();
    }

    private static List<SeedCatalogCommand.TopicSeed> topics(SeedCatalogCommand seed) {
        return seed.domains().stream()
                .flatMap(domain -> domain.fields().stream())
                .flatMap(field -> field.topics().stream())
                .toList();
    }
}
