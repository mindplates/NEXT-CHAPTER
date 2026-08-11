package com.mindplates.nextchapter.domain.admin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AI 설정 모델")
class AiSettingsModelTest {

    @Test
    @DisplayName("단계 설정은 벤더·모델만 바뀌고 단계는 고정이다")
    void withSelectionKeepsStage() {
        AiStageSetting setting =
                new AiStageSetting(1L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5", null, null, null);

        AiStageSetting updated = setting.withSelection(AiVendor.OPENAI, "gpt-x", "admin@nextchapter.local");

        assertThat(updated.stage()).isEqualTo(AiStage.SKELETON_BODY);
        assertThat(updated.vendor()).isEqualTo(AiVendor.OPENAI);
        assertThat(updated.model()).isEqualTo("gpt-x");
        assertThat(updated.updatedBy()).isEqualTo("admin@nextchapter.local");
    }

    @Test
    @DisplayName("모델은 필수다 — 빈 모델로는 호출할 수 없다")
    void modelIsRequired() {
        assertThatThrownBy(
                        () -> new AiStageSetting(1L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "  ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("임베딩 단계만 임베딩으로 식별된다 — 완성 호출과 섞이면 안 된다")
    void identifiesEmbeddingStage() {
        assertThat(AiStage.EMBEDDING.isEmbedding()).isTrue();
        assertThat(AiStage.SKELETON_BODY.isEmbedding()).isFalse();
        assertThat(AiStage.TOPIC_MAPPING.isEmbedding()).isFalse();
    }

    @Test
    @DisplayName("키 힌트는 뒤 4자만 노출한다 — 길이를 맞추려 앞자리를 흘리지 않는다")
    void hintExposesOnlyTail() {
        assertThat(AiCredential.hintOf("sk-ant-api03-abcdefgh")).isEqualTo("…efgh");
        assertThat(AiCredential.hintOf("ab")).isEqualTo("…ab");
    }

    @Test
    @DisplayName("암호문 없이 자격 증명을 만들 수 없다")
    void cipherIsRequired() {
        assertThatThrownBy(() -> AiCredential.of(AiVendor.ANTHROPIC, "  ", "…1234"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("벤더는 필수다")
    void vendorIsRequired() {
        assertThatThrownBy(() -> AiCredential.of(null, "v1:cipher", "…1234"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
