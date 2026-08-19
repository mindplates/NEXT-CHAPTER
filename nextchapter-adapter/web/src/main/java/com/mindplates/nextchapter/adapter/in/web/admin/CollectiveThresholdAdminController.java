package com.mindplates.nextchapter.adapter.in.web.admin;

import com.mindplates.nextchapter.adapter.in.web.admin.dto.UpdateCollectiveThresholdRequest;
import com.mindplates.nextchapter.application.admin.port.in.GetCollectiveThresholdSettingsUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateCollectiveThresholdSettingsUseCase;
import com.mindplates.nextchapter.application.admin.view.CollectiveThresholdView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 집단 루프 임계치 조회와 변경. */
@RestController
@RequestMapping("/api/admin/collective/threshold")
@RequiredArgsConstructor
public class CollectiveThresholdAdminController {

    private final GetCollectiveThresholdSettingsUseCase getCollectiveThresholdSettingsUseCase;
    private final UpdateCollectiveThresholdSettingsUseCase updateCollectiveThresholdSettingsUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<CollectiveThresholdView>> current() {
        return ResponseEntity.ok(ApiResponse.ok(getCollectiveThresholdSettingsUseCase.current()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<CollectiveThresholdView>> update(
            @RequestBody @Valid UpdateCollectiveThresholdRequest request, Principal principal) {
        String actor = principal == null ? null : principal.getName();
        return ResponseEntity.ok(ApiResponse.ok(updateCollectiveThresholdSettingsUseCase.update(
                request.questionThreshold(), request.minAttempts(), request.wrongRatePercent(), actor)));
    }
}
