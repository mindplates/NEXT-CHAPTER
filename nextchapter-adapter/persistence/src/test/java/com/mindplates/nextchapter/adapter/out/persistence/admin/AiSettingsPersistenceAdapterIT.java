package com.mindplates.nextchapter.adapter.out.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.admin.model.AiCredential;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiStageSetting;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({AiStageSettingPersistenceAdapter.class, AiCredentialPersistenceAdapter.class})
@DisplayName("AI 설정 어댑터 (실제 PostgreSQL)")
class AiSettingsPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    AiStageSettingPersistenceAdapter stageSettings;

    @Autowired
    AiCredentialPersistenceAdapter credentials;

    @Autowired
    EntityManager entityManager;

    /**
     * 기본값이 Java 상수가 아니라 마이그레이션이 심는 행이라는 것을 확인한다. 상수로 두면 설정이
     * 비어 있어도 호출이 성공하고, 관리 화면에서 바꾼 값이 실제로 쓰이는지 알 수 없다.
     */
    @Test
    @DisplayName("마이그레이션이 전 단계 기본값을 심어 둔다")
    void migrationSeedsDefaults() {
        assertThat(stageSettings.findAll()).hasSize(AiStage.values().length);

        assertThat(stageSettings.findByStage(AiStage.SKELETON_BODY))
                .isPresent()
                .get()
                .satisfies(setting -> {
                    assertThat(setting.vendor()).isEqualTo(AiVendor.ANTHROPIC);
                    assertThat(setting.model()).isEqualTo("claude-opus-5");
                });
    }

    /** Anthropic 이 임베딩 API 를 제공하지 않으므로 이 단계만 벤더가 다르다. */
    @Test
    @DisplayName("임베딩 단계만 다른 벤더로 심긴다")
    void embeddingStageUsesEmbeddingVendor() {
        assertThat(stageSettings.findByStage(AiStage.EMBEDDING))
                .isPresent()
                .get()
                .extracting(AiStageSetting::vendor)
                .isEqualTo(AiVendor.VOYAGE);
    }

    @Test
    @DisplayName("단계 설정은 벤더·모델만 바뀌고 단계는 그대로다")
    void updatesSelection() {
        AiStageSetting current =
                stageSettings.findByStage(AiStage.TOPIC_MAPPING).orElseThrow();

        AiStageSetting saved = stageSettings.save(
                current.withSelection(AiVendor.ANTHROPIC, "claude-sonnet-5", "admin@nextchapter.local"));

        assertThat(saved.stage()).isEqualTo(AiStage.TOPIC_MAPPING);
        assertThat(saved.model()).isEqualTo("claude-sonnet-5");
        assertThat(saved.updatedBy()).isEqualTo("admin@nextchapter.local");
    }

    @Test
    @DisplayName("벤더당 키는 하나다 — 두 행을 만들 수 없다")
    void oneCredentialPerVendor() {
        credentials.save(AiCredential.of(AiVendor.ANTHROPIC, "v1:cipher", "…1234"));

        assertThatThrownBy(() -> {
                    credentials.save(AiCredential.of(AiVendor.ANTHROPIC, "v1:other", "…5678"));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("저장된 값은 암호문과 힌트뿐이다")
    void storesOnlyCipherAndHint() {
        AiCredential saved = credentials.save(AiCredential.of(AiVendor.VOYAGE, "v1:cipher", "…1234"));

        assertThat(credentials.findByVendor(AiVendor.VOYAGE)).isPresent().get().satisfies(credential -> {
            assertThat(credential.apiKeyCipher()).isEqualTo("v1:cipher");
            assertThat(credential.keyHint()).isEqualTo("…1234");
        });
        assertThat(saved.id()).isNotNull();
    }
}
