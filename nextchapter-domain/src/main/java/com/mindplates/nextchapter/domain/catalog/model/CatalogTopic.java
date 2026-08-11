package com.mindplates.nextchapter.domain.catalog.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * L3 주제 — 카탈로그의 최말단이고 <b>뼈대가 1:1 로 붙는 유일한 계층</b>이다.
 *
 * <p>4단계를 두지 않는 이유가 여기 있다. 주제 아래를 한 번 더 쪼개면 그건 이미 뼈대 안의 챕터
 * 그래프가 표현하는 층위이고, 분류로 쪼개는 순간 뼈대가 갈라져 집단 신호가 분산된다.
 */
public record CatalogTopic(
        Long id,
        Long fieldId,
        String slug,
        String name,
        String description,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public CatalogTopic {
        if (fieldId == null) {
            throw new IllegalArgumentException("주제는 분야에 속해야 합니다.");
        }
        slug = CatalogSlugs.require(slug);
        name = Strings.requireMaxLength(Strings.requireText(name, "주제 이름"), 160, "주제 이름");
        description = Strings.trimToNull(description);
    }

    public static CatalogTopic create(Long fieldId, String slug, String name, String description, int sortOrder) {
        return new CatalogTopic(null, fieldId, slug, name, description, sortOrder, null, null);
    }

    public CatalogTopic withDetails(String name, String description, int sortOrder) {
        return new CatalogTopic(id, fieldId, slug, name, description, sortOrder, createdAt, updatedAt);
    }
}
