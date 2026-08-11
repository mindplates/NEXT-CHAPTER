package com.mindplates.nextchapter.application.admin.service;

import com.mindplates.nextchapter.application.admin.port.in.GetAiSettingsUseCase;
import com.mindplates.nextchapter.application.admin.port.in.ManageAiCredentialUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateAiStageSettingUseCase;
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
import com.mindplates.nextchapter.application.generation.service.AiVendorRegistry;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.admin.model.AiCredential;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiStageSetting;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AiSettingsService implements GetAiSettingsUseCase, UpdateAiStageSettingUseCase, ManageAiCredentialUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiSettingsService.class);

    private final LoadAiStageSettingPort loadAiStageSettingPort;
    private final SaveAiStageSettingPort saveAiStageSettingPort;
    private final LoadAiCredentialPort loadAiCredentialPort;
    private final SaveAiCredentialPort saveAiCredentialPort;
    private final DeleteAiCredentialPort deleteAiCredentialPort;
    private final SecretCipherPort secretCipherPort;
    private final AiVendorRegistry registry;

    @Override
    @Transactional(readOnly = true)
    public List<AiStageSettingView> stageSettings() {
        Set<AiVendor> withCredential = loadAiCredentialPort.findAll().stream()
                .map(AiCredential::vendor)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return loadAiStageSettingPort.findAll().stream()
                .map(setting -> toView(setting, withCredential.contains(setting.vendor())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiCredentialView> credentials() {
        return loadAiCredentialPort.findAll().stream()
                .map(credential ->
                        new AiCredentialView(credential.vendor(), credential.keyHint(), credential.updatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiVendor> supportedVendors() {
        return registry.supportedVendors().stream().sorted().toList();
    }

    /**
     * 어댑터가 없는 벤더는 저장 시점에 거절한다.
     *
     * <p>저장을 허용하면 화면은 정상으로 보이고 몇 시간 뒤 생성 잡이 실패한다. 사전 생성은 한 번에
     * 전 범위를 돌리므로 그때의 손실이 크다 — 잘못된 설정은 가장 이른 지점에서 막아야 한다.
     */
    @Override
    public AiStageSettingView update(AiStage stage, UpdateAiStageSettingCommand command) {
        AiStageSetting current = loadAiStageSettingPort
                .findByStage(stage)
                .orElseThrow(() -> new EntityNotFoundException("AI 단계 설정", stage));
        AiVendor vendor = command.vendor() == null ? current.vendor() : command.vendor();
        requireVendorSupports(stage, vendor);

        AiStageSetting saved =
                saveAiStageSettingPort.save(current.withSelection(vendor, command.model(), command.actor()));
        log.info("[AI 설정] stage={} vendor={} model={} actor={}", stage, vendor, saved.model(), command.actor());
        return toView(saved, loadAiCredentialPort.findByVendor(vendor).isPresent());
    }

    @Override
    public AiCredentialView register(RegisterAiCredentialCommand command) {
        if (command.vendor() == null) {
            throw new InvalidOperationException("벤더는 필수입니다.");
        }
        String apiKey = Strings.requireText(command.apiKey(), "API 키");

        // 벤더당 키는 하나다. 기존 행이 있으면 그 행을 덮어써 교체가 된다.
        Long existingId = loadAiCredentialPort
                .findByVendor(command.vendor())
                .map(AiCredential::id)
                .orElse(null);
        AiCredential credential = new AiCredential(
                existingId,
                command.vendor(),
                secretCipherPort.encrypt(apiKey),
                AiCredential.hintOf(apiKey),
                null,
                null);

        AiCredential saved = saveAiCredentialPort.save(credential);
        log.info("[AI 설정] {} API 키를 {}했다 hint={}", saved.vendor(), existingId == null ? "등록" : "교체", saved.keyHint());
        return new AiCredentialView(saved.vendor(), saved.keyHint(), saved.updatedAt());
    }

    @Override
    public void remove(AiVendor vendor) {
        if (loadAiCredentialPort.findByVendor(vendor).isEmpty()) {
            throw new EntityNotFoundException("API 키", vendor);
        }
        deleteAiCredentialPort.deleteByVendor(vendor);
        log.info("[AI 설정] {} API 키를 삭제했다", vendor);
    }

    private void requireVendorSupports(AiStage stage, AiVendor vendor) {
        Set<AiVendor> capable = stage.isEmbedding() ? registry.embeddingVendors() : registry.completionVendors();
        if (!capable.contains(vendor)) {
            throw new InvalidOperationException(
                    vendor + " 는 " + (stage.isEmbedding() ? "임베딩" : "완성") + " 호출을 지원하는 어댑터가 없습니다. 지원 벤더: " + capable);
        }
    }

    private AiStageSettingView toView(AiStageSetting setting, boolean credentialRegistered) {
        boolean supported = (setting.stage().isEmbedding() ? registry.embeddingVendors() : registry.completionVendors())
                .contains(setting.vendor());
        return new AiStageSettingView(
                setting.stage(),
                setting.vendor(),
                setting.model(),
                supported,
                credentialRegistered,
                setting.updatedBy(),
                setting.updatedAt());
    }
}
