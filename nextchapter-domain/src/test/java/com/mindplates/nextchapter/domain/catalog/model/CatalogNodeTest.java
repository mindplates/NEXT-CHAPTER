package com.mindplates.nextchapter.domain.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("카탈로그 노드")
class CatalogNodeTest {

    @Nested
    @DisplayName("slug")
    class Slugs {

        @Test
        @DisplayName("대문자는 소문자로 접힌다")
        void lowercases() {
            assertThat(CatalogDomain.create("Computer-Science", "컴퓨터과학", null, 0)
                            .slug())
                    .isEqualTo("computer-science");
        }

        @Test
        @DisplayName("허용하지 않는 문자가 있으면 거절한다")
        void rejectsInvalidCharacters() {
            assertThatThrownBy(() -> CatalogDomain.create("컴퓨터 과학", "컴퓨터과학", null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("slug");
        }

        @Test
        @DisplayName("빈 값은 거절한다")
        void rejectsBlank() {
            assertThatThrownBy(() -> CatalogDomain.create("  ", "컴퓨터과학", null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("부모 참조")
    class Parents {

        @Test
        @DisplayName("분야는 도메인 없이 만들 수 없다")
        void fieldRequiresDomain() {
            assertThatThrownBy(() -> CatalogField.create(null, "ai", "인공지능", null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("도메인");
        }

        @Test
        @DisplayName("주제는 분야 없이 만들 수 없다")
        void topicRequiresField() {
            assertThatThrownBy(() -> CatalogTopic.create(null, "ml", "머신러닝", null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("분야");
        }
    }

    @Test
    @DisplayName("수정은 표시 정보만 바꾸고 slug 는 그대로 둔다")
    void withDetailsKeepsSlug() {
        CatalogTopic topic = new CatalogTopic(7L, 3L, "machine-learning", "머신러닝", "설명", 0, null, null);

        CatalogTopic updated = topic.withDetails("기계학습 개론", "새 설명", 5);

        assertThat(updated.slug()).isEqualTo("machine-learning");
        assertThat(updated.id()).isEqualTo(7L);
        assertThat(updated.fieldId()).isEqualTo(3L);
        assertThat(updated.name()).isEqualTo("기계학습 개론");
        assertThat(updated.sortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("설명의 빈 문자열은 null 로 정리된다")
    void blankDescriptionBecomesNull() {
        assertThat(CatalogDomain.create("cs", "컴퓨터과학", "   ", 0).description()).isNull();
    }
}
