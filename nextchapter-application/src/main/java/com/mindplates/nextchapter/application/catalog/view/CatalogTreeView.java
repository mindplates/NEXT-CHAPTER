package com.mindplates.nextchapter.application.catalog.view;

import java.util.List;

/**
 * 카탈로그 3단계를 한 번에 표현한다. 관리 화면의 트리와 매핑 프롬프트의 후보 목록이 같은 모양을
 * 필요로 하므로 하나만 만든다.
 */
public record CatalogTreeView(List<DomainNode> domains) {

    public record DomainNode(Long id, String slug, String name, String description, List<FieldNode> fields) {}

    public record FieldNode(Long id, String slug, String name, String description, List<TopicNode> topics) {}

    /** {@code hasSkeleton} 은 이 주제에 이미 뼈대가 붙어 있는지다 — 신규 생성 비용이 드는지의 표시. */
    public record TopicNode(Long id, String slug, String name, String description, boolean hasSkeleton) {}
}
