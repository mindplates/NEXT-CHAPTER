package com.mindplates.nextchapter.adapter.in.web.generation;

import com.mindplates.nextchapter.application.generation.port.in.ManageGenerationFailureUseCase;
import com.mindplates.nextchapter.application.generation.view.GenerationFailureView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생성 실패 운영자 큐 — 목록 · 재시도 · 닫기.
 *
 * <p>큐가 화면에 있어야 하는 이유는 생성 실패가 <b>조용히 멈추는</b> 종류이기 때문이다. 재시도가 끝나면
 * 메시지는 사라지고 뼈대는 생성 중 상태로 남는데, 사용자는 완료 알림을 계속 기다린다.
 */
@RestController
@RequestMapping("/api/admin/generation-failures")
@RequiredArgsConstructor
public class GenerationFailureAdminController {

    private final ManageGenerationFailureUseCase manageGenerationFailureUseCase;

    /** {@code status} 를 주지 않으면 <b>OPEN</b> 만 본다. 큐의 기본 질문이 "지금 손댈 것이 무엇인가"다. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GenerationFailureView>>> list(
            @RequestParam(required = false, defaultValue = "OPEN") String status) {
        return ResponseEntity.ok(ApiResponse.ok(manageGenerationFailureUseCase.list(parseStatus(status))));
    }

    @GetMapping("/skeletons/{skeletonId}")
    public ResponseEntity<ApiResponse<List<GenerationFailureView>>> bySkeleton(@PathVariable Long skeletonId) {
        return ResponseEntity.ok(ApiResponse.ok(manageGenerationFailureUseCase.bySkeletonId(skeletonId)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<GenerationFailureView>> retry(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(manageGenerationFailureUseCase.retry(id, actorOf(principal))));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<GenerationFailureView>> resolve(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(manageGenerationFailureUseCase.resolve(id, actorOf(principal))));
    }

    /** {@code ALL} 은 상태 없이(전체) 조회다. 처리된 항목까지 훑어야 같은 원인의 반복이 보인다. */
    private static GenerationFailureStatus parseStatus(String status) {
        return "ALL".equalsIgnoreCase(status) ? null : GenerationFailureStatus.valueOf(status.toUpperCase());
    }

    private static String actorOf(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
