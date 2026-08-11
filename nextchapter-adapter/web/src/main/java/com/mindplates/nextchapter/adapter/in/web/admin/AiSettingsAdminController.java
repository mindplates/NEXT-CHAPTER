package com.mindplates.nextchapter.adapter.in.web.admin;

import com.mindplates.nextchapter.adapter.in.web.admin.dto.RegisterAiCredentialRequest;
import com.mindplates.nextchapter.adapter.in.web.admin.dto.UpdateAiStageSettingRequest;
import com.mindplates.nextchapter.application.admin.port.in.GetAiSettingsUseCase;
import com.mindplates.nextchapter.application.admin.port.in.ManageAiCredentialUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateAiStageSettingUseCase;
import com.mindplates.nextchapter.application.admin.view.AiCredentialView;
import com.mindplates.nextchapter.application.admin.view.AiStageSettingView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 단계별 벤더·모델 지정과 벤더별 API 키 등록·교체. */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiSettingsAdminController {

    private final GetAiSettingsUseCase getAiSettingsUseCase;
    private final UpdateAiStageSettingUseCase updateAiStageSettingUseCase;
    private final ManageAiCredentialUseCase manageAiCredentialUseCase;

    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<AiStageSettingView>>> stages() {
        return ResponseEntity.ok(ApiResponse.ok(getAiSettingsUseCase.stageSettings()));
    }

    /** 관리 화면의 벤더 선택지. 어댑터가 등록된 벤더만 나온다. */
    @GetMapping("/vendors")
    public ResponseEntity<ApiResponse<List<AiVendor>>> vendors() {
        return ResponseEntity.ok(ApiResponse.ok(getAiSettingsUseCase.supportedVendors()));
    }

    @PutMapping("/stages/{stage}")
    public ResponseEntity<ApiResponse<AiStageSettingView>> updateStage(
            @PathVariable AiStage stage, @RequestBody @Valid UpdateAiStageSettingRequest request, Principal principal) {
        String actor = principal == null ? null : principal.getName();
        return ResponseEntity.ok(ApiResponse.ok(updateAiStageSettingUseCase.update(stage, request.toCommand(actor))));
    }

    @GetMapping("/credentials")
    public ResponseEntity<ApiResponse<List<AiCredentialView>>> credentials() {
        return ResponseEntity.ok(ApiResponse.ok(getAiSettingsUseCase.credentials()));
    }

    @PostMapping("/credentials")
    public ResponseEntity<ApiResponse<AiCredentialView>> registerCredential(
            @RequestBody @Valid RegisterAiCredentialRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(manageAiCredentialUseCase.register(request.toCommand())));
    }

    @DeleteMapping("/credentials/{vendor}")
    public ResponseEntity<ApiResponse<Void>> removeCredential(@PathVariable AiVendor vendor) {
        manageAiCredentialUseCase.remove(vendor);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
