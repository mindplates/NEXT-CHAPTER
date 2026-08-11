package com.mindplates.nextchapter.adapter.in.web.admin;

import com.mindplates.nextchapter.adapter.in.web.admin.dto.UpdateAiBudgetRequest;
import com.mindplates.nextchapter.application.admin.port.in.GetAiBudgetUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateAiBudgetUseCase;
import com.mindplates.nextchapter.application.admin.view.AiBudgetView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 뼈대당·일일 토큰 상한 조회와 변경. */
@RestController
@RequestMapping("/api/admin/ai/budget")
@RequiredArgsConstructor
public class AiBudgetAdminController {

    private final GetAiBudgetUseCase getAiBudgetUseCase;
    private final UpdateAiBudgetUseCase updateAiBudgetUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<AiBudgetView>> current() {
        return ResponseEntity.ok(ApiResponse.ok(getAiBudgetUseCase.current()));
    }

    /** 검수 화면이 "이 뼈대가 얼마를 썼나"를 보여준다 — 상한을 올릴지 판단하는 근거다. */
    @GetMapping("/skeletons/{skeletonId}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> skeletonUsage(@PathVariable Long skeletonId) {
        return ResponseEntity.ok(
                ApiResponse.ok(Map.of("usedTokens", getAiBudgetUseCase.usedTokensBySkeleton(skeletonId))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AiBudgetView>> update(
            @RequestBody @Valid UpdateAiBudgetRequest request, Principal principal) {
        String actor = principal == null ? null : principal.getName();
        return ResponseEntity.ok(ApiResponse.ok(
                updateAiBudgetUseCase.update(request.perSkeletonTokenLimit(), request.dailyTokenLimit(), actor)));
    }
}
