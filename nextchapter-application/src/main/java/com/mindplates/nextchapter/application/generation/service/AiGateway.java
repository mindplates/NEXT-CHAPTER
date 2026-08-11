package com.mindplates.nextchapter.application.generation.service;

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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단계 이름만으로 AI 를 호출하는 단일 창구.
 *
 * <p>호출부는 벤더도 모델도 키도 모른다. "이 단계로 이런 프롬프트" 만 넘기고, 무엇을 쓸지는 관리 화면
 * 설정이 정한다. 이 경계가 없으면 모델 문자열이 호출부마다 흩어지고, "코드에 하드코딩된 모델이 없다"는
 * 조건이 유지될 수 없다.
 *
 * <p><b>폴백을 두지 않는다.</b> 설정 행이 없거나 어댑터가 없거나 키가 없으면 즉시 실패한다. 기본
 * 모델로 조용히 넘어가면 관리 화면에서 바꾼 값이 실제로 쓰이는지 확인할 방법이 없고, 잘못된 모델로
 * 뼈대 하나를 다 생성한 뒤에야 알게 된다.
 *
 * <p>키를 매 호출 복호화하는 것은 의도된 비용이다. 캐시하면 관리 화면에서 키를 교체해도 재기동
 * 전까지 옛 키가 쓰인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final LoadAiStageSettingPort loadAiStageSettingPort;
    private final LoadAiCredentialPort loadAiCredentialPort;
    private final SecretCipherPort secretCipherPort;
    private final AiVendorRegistry registry;

    public LlmCompletionResult complete(AiStage stage, String systemPrompt, String userPrompt, int maxTokens) {
        if (stage.isEmbedding()) {
            throw new InvalidOperationException("임베딩 단계는 완성 호출에 쓸 수 없습니다.");
        }
        AiStageSetting setting = requireSetting(stage);
        LlmCompletionClientPort client = registry.completionClient(setting.vendor())
                .orElseThrow(() -> unsupported(stage, setting.vendor(), "완성"));

        LlmCompletionResult result = client.complete(new LlmCompletionRequest(
                setting.model(), decryptedKey(setting.vendor()), systemPrompt, userPrompt, maxTokens, null));
        log.debug(
                "[AI] stage={} vendor={} model={} tokens={}",
                stage,
                setting.vendor(),
                setting.model(),
                result.totalTokens());
        return result;
    }

    public EmbeddingResult embed(List<String> inputs) {
        AiStageSetting setting = requireSetting(AiStage.EMBEDDING);
        EmbeddingClientPort client = registry.embeddingClient(setting.vendor())
                .orElseThrow(() -> unsupported(AiStage.EMBEDDING, setting.vendor(), "임베딩"));

        return client.embed(new EmbeddingRequest(setting.model(), decryptedKey(setting.vendor()), inputs));
    }

    /** 지금 임베딩 단계에 설정된 모델. 저장된 벡터가 현재 모델로 만들어진 것인지 판정하는 기준이다. */
    public String currentEmbeddingModel() {
        return requireSetting(AiStage.EMBEDDING).model();
    }

    private AiStageSetting requireSetting(AiStage stage) {
        return loadAiStageSettingPort
                .findByStage(stage)
                .orElseThrow(
                        () -> new InvalidOperationException(stage + " 단계의 AI 설정이 없습니다. 관리 화면에서 벤더와 모델을 지정해야 합니다."));
    }

    private String decryptedKey(AiVendor vendor) {
        AiCredential credential = loadAiCredentialPort
                .findByVendor(vendor)
                .orElseThrow(() -> new InvalidOperationException(vendor + " 의 API 키가 등록돼 있지 않습니다. 관리 화면에서 등록해야 합니다."));
        return secretCipherPort.decrypt(credential.apiKeyCipher());
    }

    private static InvalidOperationException unsupported(AiStage stage, AiVendor vendor, String kind) {
        return new InvalidOperationException(stage + " 단계에 지정된 벤더 " + vendor + " 의 " + kind + " 어댑터가 없습니다.");
    }
}
