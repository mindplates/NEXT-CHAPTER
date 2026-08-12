package com.mindplates.nextchapter.application.catalog.port.in.command;

import java.util.List;

/**
 * 초기 카탈로그. <b>도메인 → 분야 → 주제 → 별칭</b> 네 층을 한 번에 담는다.
 *
 * <p>분야가 주제보다 많아도 된다. 분야는 LLM 매핑이 층별 좁히기를 하는 <b>발판</b>이고(후보를 수십 개로
 * 유지한다), 주제는 뼈대가 붙는 지점이라 소수여야 한다 — 넓게 벌리면 뼈대당 사용자가 1~2명씩 붙어 집단
 * 루프가 임계치를 못 넘는다.
 */
public record SeedCatalogCommand(List<DomainSeed> domains) {

    public SeedCatalogCommand {
        domains = domains == null ? List.of() : List.copyOf(domains);
    }

    public record DomainSeed(String slug, String name, String description, List<FieldSeed> fields) {

        public DomainSeed {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record FieldSeed(String slug, String name, String description, List<TopicSeed> topics) {

        public FieldSeed {
            topics = topics == null ? List.of() : List.copyOf(topics);
        }
    }

    /**
     * @param aliases 같은 주제를 가리키는 다른 표현. <b>가장 싼 수렴 수단</b>이다 — 별칭이 맞으면 LLM 을
     *     부르지 않으므로 비용도 지연도 0이고 결과가 결정적이다
     */
    public record TopicSeed(String slug, String name, String description, List<String> aliases) {

        public TopicSeed {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
