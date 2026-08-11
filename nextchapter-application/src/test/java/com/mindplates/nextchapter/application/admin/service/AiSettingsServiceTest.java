package com.mindplates.nextchapter.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.in.command.RegisterAiCredentialCommand;
import com.mindplates.nextchapter.application.admin.port.in.command.UpdateAiStageSettingCommand;
import com.mindplates.nextchapter.application.admin.port.out.DeleteAiCredentialPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiCredentialPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiStageSettingPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiCredentialPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiStageSettingPort;
import com.mindplates.nextchapter.application.admin.port.out.SecretCipherPort;
import com.mindplates.nextchapter.application.admin.view.AiCredentialView;
import com.mindplates.nextchapter.application.admin.view.AiStageSettingView;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingClientPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionClientPort;
import com.mindplates.nextchapter.application.generation.service.AiVendorRegistry;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.AiCredential;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiStageSetting;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI 단계 설정과 키 관리")
class AiSettingsServiceTest {

    @Mock
    LoadAiStageSettingPort loadAiStageSettingPort;

    @Mock
    SaveAiStageSettingPort saveAiStageSettingPort;

    @Mock
    LoadAiCredentialPort loadAiCredentialPort;

    @Mock
    SaveAiCredentialPort saveAiCredentialPort;

    @Mock
    DeleteAiCredentialPort deleteAiCredentialPort;

    @Mock
    SecretCipherPort secretCipherPort;

    AiSettingsService service;

    /**
     * 레지스트리는 목이 아니라 실제 인스턴스를 쓴다. 검증 대상이 "어떤 벤더가 지원되는가"의 판정이고,
     * 그 판정은 레지스트리가 주입된 구현에서 역산하는 로직 자체다 — 목으로 바꾸면 그 로직이 빠진다.
     */
    @BeforeEach
    void setUp() {
        AiVendorRegistry registry = new AiVendorRegistry(
                List.of(completionClient(AiVendor.ANTHROPIC)), List.of(embeddingClient(AiVendor.VOYAGE)));
        service = new AiSettingsService(
                loadAiStageSettingPort,
                saveAiStageSettingPort,
                loadAiCredentialPort,
                saveAiCredentialPort,
                deleteAiCredentialPort,
                secretCipherPort,
                registry);
    }

    @Test
    @DisplayName("어댑터가 없는 벤더는 저장 시점에 거절한다 — 몇 시간 뒤 생성 잡에서 실패하면 늦다")
    void rejectsUnsupportedVendor() {
        when(loadAiStageSettingPort.findByStage(AiStage.SKELETON_BODY)).thenReturn(Optional.of(setting()));

        assertThatThrownBy(() -> service.update(
                        AiStage.SKELETON_BODY, new UpdateAiStageSettingCommand(AiVendor.OPENAI, "gpt-x", "admin")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("OPENAI");

        verify(saveAiStageSettingPort, never()).save(any());
    }

    @Test
    @DisplayName("완성 단계에 임베딩 전용 벤더를 지정할 수 없다")
    void rejectsEmbeddingOnlyVendorForCompletion() {
        when(loadAiStageSettingPort.findByStage(AiStage.SKELETON_BODY)).thenReturn(Optional.of(setting()));

        assertThatThrownBy(() -> service.update(
                        AiStage.SKELETON_BODY,
                        new UpdateAiStageSettingCommand(AiVendor.VOYAGE, "voyage-3-large", "admin")))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("임베딩 단계에는 임베딩 벤더를 지정할 수 있다")
    void allowsEmbeddingVendorForEmbeddingStage() {
        AiStageSetting embedding =
                new AiStageSetting(2L, AiStage.EMBEDDING, AiVendor.VOYAGE, "voyage-3-large", null, null, null);
        when(loadAiStageSettingPort.findByStage(AiStage.EMBEDDING)).thenReturn(Optional.of(embedding));
        when(saveAiStageSettingPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(loadAiCredentialPort.findByVendor(AiVendor.VOYAGE)).thenReturn(Optional.empty());

        AiStageSettingView view = service.update(
                AiStage.EMBEDDING, new UpdateAiStageSettingCommand(AiVendor.VOYAGE, "voyage-3-large", "admin"));

        assertThat(view.vendorSupported()).isTrue();
        // 키가 없으면 설정은 맞아도 준비된 상태가 아니다. 화면에서 이 차이가 보여야 한다.
        assertThat(view.credentialRegistered()).isFalse();
        assertThat(view.isReady()).isFalse();
    }

    @Test
    @DisplayName("키는 암호화해 저장하고 힌트만 돌려준다")
    void encryptsApiKeyAndReturnsOnlyHint() {
        when(loadAiCredentialPort.findByVendor(AiVendor.ANTHROPIC)).thenReturn(Optional.empty());
        when(secretCipherPort.encrypt("sk-ant-1234")).thenReturn("v1:cipher");
        when(saveAiCredentialPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AiCredentialView view = service.register(new RegisterAiCredentialCommand(AiVendor.ANTHROPIC, "sk-ant-1234"));

        assertThat(view.keyHint()).isEqualTo("…1234");
        verify(secretCipherPort).encrypt("sk-ant-1234");
    }

    @Test
    @DisplayName("이미 키가 있으면 같은 행을 덮어써 교체가 된다 — 벤더당 키는 하나다")
    void replacesExistingCredential() {
        when(loadAiCredentialPort.findByVendor(AiVendor.ANTHROPIC))
                .thenReturn(Optional.of(new AiCredential(7L, AiVendor.ANTHROPIC, "v1:old", "…0000", null, null)));
        when(secretCipherPort.encrypt(any())).thenReturn("v1:new");
        when(saveAiCredentialPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new RegisterAiCredentialCommand(AiVendor.ANTHROPIC, "sk-ant-9999"));

        verify(saveAiCredentialPort)
                .save(org.mockito.ArgumentMatchers.argThat(credential -> credential.id() == 7L
                        && "v1:new".equals(credential.apiKeyCipher())
                        && "…9999".equals(credential.keyHint())));
    }

    @Test
    @DisplayName("지원 벤더 목록은 등록된 어댑터에서 역산한다")
    void supportedVendorsComeFromAdapters() {
        assertThat(service.supportedVendors()).containsExactly(AiVendor.ANTHROPIC, AiVendor.VOYAGE);
    }

    private static AiStageSetting setting() {
        return new AiStageSetting(1L, AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5", null, null, null);
    }

    private static LlmCompletionClientPort completionClient(AiVendor vendor) {
        return new LlmCompletionClientPort() {
            @Override
            public AiVendor vendor() {
                return vendor;
            }

            @Override
            public com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult complete(
                    com.mindplates.nextchapter.application.generation.port.out.LlmCompletionRequest request) {
                throw new UnsupportedOperationException("이 테스트는 호출하지 않는다");
            }
        };
    }

    private static EmbeddingClientPort embeddingClient(AiVendor vendor) {
        return new EmbeddingClientPort() {
            @Override
            public AiVendor vendor() {
                return vendor;
            }

            @Override
            public com.mindplates.nextchapter.application.generation.port.out.EmbeddingResult embed(
                    com.mindplates.nextchapter.application.generation.port.out.EmbeddingRequest request) {
                throw new UnsupportedOperationException("이 테스트는 호출하지 않는다");
            }
        };
    }
}
