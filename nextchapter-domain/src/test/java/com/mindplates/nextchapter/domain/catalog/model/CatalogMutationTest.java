package com.mindplates.nextchapter.domain.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카탈로그 노드 수정")
class CatalogMutationTest {

    @Test
    @DisplayName("도메인 수정은 slug 와 ID 를 유지한다")
    void domainKeepsIdentity() {
        CatalogDomain updated =
                new CatalogDomain(1L, "cs", "컴퓨터과학", "설명", 0, null, null).withDetails("컴퓨터 과학", "새 설명", 7);

        assertThat(updated.id()).isEqualTo(1L);
        assertThat(updated.slug()).isEqualTo("cs");
        assertThat(updated.name()).isEqualTo("컴퓨터 과학");
        assertThat(updated.description()).isEqualTo("새 설명");
        assertThat(updated.sortOrder()).isEqualTo(7);
    }

    @Test
    @DisplayName("분야 수정은 부모 도메인을 바꾸지 않는다 — 소속 이동은 별도 조작이어야 한다")
    void fieldKeepsParent() {
        CatalogField updated = new CatalogField(10L, 1L, "ai", "인공지능", null, 0, null, null).withDetails("AI", null, 2);

        assertThat(updated.domainId()).isEqualTo(1L);
        assertThat(updated.slug()).isEqualTo("ai");
        assertThat(updated.name()).isEqualTo("AI");
    }

    @Test
    @DisplayName("뼈대는 상태 전이마다 새 인스턴스이고 주제는 고정이다")
    void skeletonKeepsTopic() {
        Skeleton skeleton = new Skeleton(5L, 42L, SkeletonStatus.GENERATING_BODIES, null, null);

        Skeleton advanced = skeleton.transitionTo(SkeletonStatus.GENERATING_ASSETS);

        assertThat(advanced.topicId()).isEqualTo(42L);
        assertThat(advanced.id()).isEqualTo(5L);
        assertThat(advanced.status()).isEqualTo(SkeletonStatus.GENERATING_ASSETS);
    }
}
