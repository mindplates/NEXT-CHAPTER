package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.out.LoadAiCredentialPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiStageSettingPort;
import com.mindplates.nextchapter.application.admin.port.out.SecretCipherPort;
import com.mindplates.nextchapter.application.admin.service.AiBudgetService;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingClientPort;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingRequest;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionClientPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionRequest;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
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

    @Mock
    AiBudgetService aiBudgetService;

    @Test
    @DisplayName("설정된 모델과 복호화한 키로 호출한다")
    void passesConfiguredModelAndDecryptedKey() {
        RecordingCompletionClient client = new RecordingCompletionClient(AiVendor.ANTHROPIC);
        AiGateway gateway = gateway(List.of(client), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5");
        stubCredential(AiVendor.ANTHROPIC);

        LlmCompletionResult result = gateway.complete(AiStage.SKELETON_BODY, 7L, "시스템", "사용자", 2048);

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

        assertThatThrownBy(() -> gateway.complete(AiStage.VERIFICATION, null, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("AI 설정이 없습니다");
    }

    @Test
    @DisplayName("키가 등록돼 있지 않으면 실패한다")
    void failsWithoutCredential() {
        AiGateway gateway = gateway(List.of(new RecordingCompletionClient(AiVendor.ANTHROPIC)), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5");
        when(loadAiCredentialPort.findByVendor(AiVendor.ANTHROPIC)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gateway.complete(AiStage.SKELETON_BODY, null, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("API 키");
    }

    @Test
    @DisplayName("어댑터가 없는 벤더가 설정돼 있으면 실패한다")
    void failsWhenVendorHasNoAdapter() {
        AiGateway gateway = gateway(List.of(), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.OPENAI, "gpt-x");

        assertThatThrownBy(() -> gateway.complete(AiStage.SKELETON_BODY, null, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("어댑터가 없습니다");
    }

    @Test
    @DisplayName("임베딩 단계를 완성 호출에 쓸 수 없다")
    void rejectsEmbeddingStageForCompletion() {
        AiGateway gateway = gateway(List.of(), List.of());

        assertThatThrownBy(() -> gateway.complete(AiStage.EMBEDDING, null, null, "질문", 100))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("임베딩은 EMBEDDING 단계 설정을 따른다")
    void embedUsesEmbeddingStage() {
        RecordingEmbeddingClient client = new RecordingEmbeddingClient(AiVendor.VOYAGE);
        AiGateway gateway = gateway(List.of(), List.of(client));
        stubSetting(AiStage.EMBEDDING, AiVendor.VOYAGE, "voyage-3-large");
        stubCredential(AiVendor.VOYAGE);

        EmbeddingResult result = gateway.embed(null, List.of("머신러닝"));

        assertThat(client.lastRequest.model()).isEqualTo("voyage-3-large");
        assertThat(result.model()).isEqualTo("voyage-3-large");
        assertThat(gateway.currentEmbeddingModel()).isEqualTo("voyage-3-large");
    }

    /**
     * 상한 판정이 <b>벤더 호출 전에</b> 일어나야 한다. 뒤에 하면 이미 청구된 뒤에 막는 것이고, 사전 생성은
     * 그 한 번이 큰 호출이다.
     */
    @Test
    @DisplayName("상한을 넘으면 벤더를 부르지 않는다")
    void doesNotCallVendorWhenBudgetExceeded() {
        RecordingCompletionClient client = new RecordingCompletionClient(AiVendor.ANTHROPIC);
        AiGateway gateway = gateway(List.of(client), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5");
        org.mockito.Mockito.doThrow(new BudgetExceededException("일일 토큰 상한을 넘어 생성을 중단합니다."))
                .when(aiBudgetService)
                .requireWithinBudget(7L, 2048);

        assertThatThrownBy(() -> gateway.complete(AiStage.SKELETON_BODY, 7L, "시스템", "사용자", 2048))
                .isInstanceOf(BudgetExceededException.class);

        assertThat(client.lastRequest).isNull();
        org.mockito.Mockito.verify(aiBudgetService, org.mockito.Mockito.never())
                .recordUsage(any(), any(), any(), anyString(), anyInt(), anyInt());
    }

    /** 기록이 빠지면 다음 판정의 기준이 틀린다 — 상한은 있는데 사실상 무제한이 되고, 에러는 나지 않는다. */
    @Test
    @DisplayName("완성 호출의 사용량을 뼈대에 귀속시켜 기록한다")
    void recordsCompletionUsage() {
        AiGateway gateway = gateway(List.of(new RecordingCompletionClient(AiVendor.ANTHROPIC)), List.of());
        stubSetting(AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5");
        stubCredential(AiVendor.ANTHROPIC);

        gateway.complete(AiStage.SKELETON_BODY, 7L, "시스템", "사용자", 2048);

        org.mockito.Mockito.verify(aiBudgetService)
                .recordUsage(7L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5", 10, 20);
    }

    /**
     * 임베딩에는 {@code maxTokens} 가 없다. 판정에 0 을 넘기면 상한이 아무리 낮아도 통과하므로 입력 길이로
     * 어림한다 — 과하게 잡는 쪽이 예산을 넘기는 쪽보다 낫다.
     */
    @Test
    @DisplayName("임베딩은 입력 길이로 상한을 어림하고, 실제 사용량을 기록한다")
    void estimatesAndRecordsEmbeddingUsage() {
        AiGateway gateway = gateway(List.of(), List.of(new RecordingEmbeddingClient(AiVendor.VOYAGE)));
        stubSetting(AiStage.EMBEDDING, AiVendor.VOYAGE, "voyage-3-large");
        stubCredential(AiVendor.VOYAGE);

        gateway.embed(null, List.of("머신러닝"));

        org.mockito.ArgumentCaptor<Integer> estimated = org.mockito.ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(aiBudgetService)
                .requireWithinBudget(org.mockito.ArgumentMatchers.isNull(), estimated.capture());
        assertThat(estimated.getValue()).isPositive();
        org.mockito.Mockito.verify(aiBudgetService)
                .recordUsage(null, AiStage.EMBEDDING, AiVendor.VOYAGE, "voyage-3-large", 5, 0);
    }

    private AiGateway gateway(List<LlmCompletionClientPort> completion, List<EmbeddingClientPort> embedding) {
        return gateway(completion, embedding, List.of());
    }

    private AiGateway gateway(
            List<LlmCompletionClientPort> completion,
            List<EmbeddingClientPort> embedding,
            List<com.mindplates.nextchapter.application.generation.port.out.LlmBatchClientPort> batch) {
        return new AiGateway(
                loadAiStageSettingPort,
                loadAiCredentialPort,
                secretCipherPort,
                new AiVendorRegistry(completion, embedding, batch),
                aiBudgetService);
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
