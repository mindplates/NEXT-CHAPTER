package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.out.LoadAiCredentialPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiStageSettingPort;
import com.mindplates.nextchapter.application.admin.port.out.SecretCipherPort;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingClientPort;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingRequest;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionClientPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionRequest;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.AiCredential;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiStageSetting;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 게이트웨이가 지켜야 하는 것은 "설정된 모델·키로 호출한다"와 "폴백하지 않는다" 둘이다.
 *
 * <p>후자가 더 중요하다. 기본 모델로 조용히 넘어가면 관리 화면에서 바꾼 값이 실제로 쓰이는지 확인할
 * 수 없고, 사전 생성은 뼈대 하나를 통째로 돌리므로 잘못된 모델의 대가가 크다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 게이트웨이")
class AiGatewayTest {

    @Mock
    LoadAiStageSettingPort loadAiStageSettingPort;

    @Mock
    LoadAiCredentialPort loadAiCredentialPort;

    @Mock
    SecretCipherPort secretCipherPort;

    @Test
    @DisplayName("설정된 모델과 복호화한 키로 호출한다")
    void passesConfiguredModelAndDecryptedKey() {
        RecordingCompletionClient client = new RecordingCompletionClient(AiVendor.ANTHROPIC);
        AiGateway gateway = gateway(List.of(client), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5");
        stubCredential(AiVendor.ANTHROPIC);

        LlmCompletionResult result = gateway.complete(AiStage.SKELETON_BODY, "시스템", "사용자", 2048);

        assertThat(client.lastRequest.model()).isEqualTo("claude-opus-5");
        assertThat(client.lastRequest.apiKey()).isEqualTo("plain-key");
        assertThat(client.lastRequest.maxTokens()).isEqualTo(2048);
        assertThat(result.text()).isEqualTo("응답");
    }

    @Test
    @DisplayName("설정 행이 없으면 폴백하지 않고 실패한다")
    void failsWithoutSetting() {
        AiGateway gateway = gateway(List.of(new RecordingCompletionClient(AiVendor.ANTHROPIC)), List.of());
        when(loadAiStageSettingPort.findByStage(AiStage.VERIFICATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gateway.complete(AiStage.VERIFICATION, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("AI 설정이 없습니다");
    }

    @Test
    @DisplayName("키가 등록돼 있지 않으면 실패한다")
    void failsWithoutCredential() {
        AiGateway gateway = gateway(List.of(new RecordingCompletionClient(AiVendor.ANTHROPIC)), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5");
        when(loadAiCredentialPort.findByVendor(AiVendor.ANTHROPIC)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gateway.complete(AiStage.SKELETON_BODY, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("API 키");
    }

    @Test
    @DisplayName("어댑터가 없는 벤더가 설정돼 있으면 실패한다")
    void failsWhenVendorHasNoAdapter() {
        AiGateway gateway = gateway(List.of(), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.OPENAI, "gpt-x");

        assertThatThrownBy(() -> gateway.complete(AiStage.SKELETON_BODY, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("어댑터가 없습니다");
    }

    @Test
    @DisplayName("임베딩 단계를 완성 호출에 쓸 수 없다")
    void rejectsEmbeddingStageForCompletion() {
        AiGateway gateway = gateway(List.of(), List.of());

        assertThatThrownBy(() -> gateway.complete(AiStage.EMBEDDING, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("임베딩은 EMBEDDING 단계 설정을 따른다")
    void embedUsesEmbeddingStage() {
        RecordingEmbeddingClient client = new RecordingEmbeddingClient(AiVendor.VOYAGE);
        AiGateway gateway = gateway(List.of(), List.of(client));
        stubSetting(AiStage.EMBEDDING, AiVendor.VOYAGE, "voyage-3-large");
        stubCredential(AiVendor.VOYAGE);

        EmbeddingResult result = gateway.embed(List.of("머신러닝"));

        assertThat(client.lastRequest.model()).isEqualTo("voyage-3-large");
        assertThat(result.model()).isEqualTo("voyage-3-large");
        assertThat(gateway.currentEmbeddingModel()).isEqualTo("voyage-3-large");
    }

    private AiGateway gateway(List<LlmCompletionClientPort> completion, List<EmbeddingClientPort> embedding) {
        return new AiGateway(
                loadAiStageSettingPort,
                loadAiCredentialPort,
                secretCipherPort,
                new AiVendorRegistry(completion, embedding));
    }

    private void stubSetting(AiStage stage, AiVendor vendor, String model) {
        when(loadAiStageSettingPort.findByStage(stage))
                .thenReturn(Optional.of(new AiStageSetting(1L, stage, vendor, model, null, null, null)));
    }

    private void stubCredential(AiVendor vendor) {
        when(loadAiCredentialPort.findByVendor(vendor))
                .thenReturn(Optional.of(new AiCredential(1L, vendor, "v1:cipher", "…1234", null, null)));
        when(secretCipherPort.decrypt("v1:cipher")).thenReturn("plain-key");
    }

    private static final class RecordingCompletionClient implements LlmCompletionClientPort {

        private final AiVendor vendor;
        private LlmCompletionRequest lastRequest;

        private RecordingCompletionClient(AiVendor vendor) {
            this.vendor = vendor;
        }

        @Override
        public AiVendor vendor() {
            return vendor;
        }

        @Override
        public LlmCompletionResult complete(LlmCompletionRequest request) {
            this.lastRequest = request;
            return new LlmCompletionResult("응답", 10, 20);
        }
    }

    private static final class RecordingEmbeddingClient implements EmbeddingClientPort {

        private final AiVendor vendor;
        private EmbeddingRequest lastRequest;

        private RecordingEmbeddingClient(AiVendor vendor) {
            this.vendor = vendor;
        }

        @Override
        public AiVendor vendor() {
            return vendor;
        }

        @Override
        public EmbeddingResult embed(EmbeddingRequest request) {
            this.lastRequest = request;
            return new EmbeddingResult(request.model(), 3, List.of(new float[] {0.1f, 0.2f, 0.3f}), 5);
        }
    }
}
