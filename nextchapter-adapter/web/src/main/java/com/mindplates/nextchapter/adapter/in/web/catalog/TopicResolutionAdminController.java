package com.mindplates.nextchapter.adapter.in.web.catalog;

import com.mindplates.nextchapter.adapter.in.web.catalog.dto.ConfirmTopicCandidateRequest;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.ResolveTopicInputRequest;
import com.mindplates.nextchapter.application.catalog.port.in.ResolveTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주제 입력 해소. 지금은 관리자 경로에만 있다.
 *
 * <p>사용자 경로(`/api/topics/resolve`)로 여는 것은 M3 다. 이 엔드포인트는 호출마다 LLM 을 최대 3번
 * 부르므로, 인증 없이 열면 비용 남용 경로가 그대로 생긴다. 사용자 소셜 로그인과 레이트 리밋이 함께
 * 들어오는 M3 에서 옮긴다 — 그전에 열어 두면 "나중에 잠그기"를 잊는 쪽이 기본값이 된다.
 *
 * <p>M1 동안 카탈로그가 비어 있어 사용자 경로가 열려도 대부분 검토로 떨어진다는 점도 이 순서를
 * 지지한다. 시드 투입은 M3 P3.7 이다.
 */
@RestController
@RequestMapping("/api/admin/catalog/resolve")
@RequiredArgsConstructor
public class TopicResolutionAdminController {

    private final ResolveTopicInputUseCase resolveTopicInputUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<TopicResolutionView>> resolve(
            @RequestBody @Valid ResolveTopicInputRequest request, Principal principal) {
        String actor = principal == null ? null : principal.getName();
        return ResponseEntity.ok(ApiResponse.ok(resolveTopicInputUseCase.resolve(request.input(), actor)));
    }

    @PostMapping("/{requestId}/confirm")
    public ResponseEntity<ApiResponse<TopicResolutionView>> confirm(
            @PathVariable Long requestId, @RequestBody @Valid ConfirmTopicCandidateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(resolveTopicInputUseCase.confirmCandidate(requestId, request.topicId())));
    }

    /** 후보 전부 거절. 붙일 분야가 있으면 신규 주제가 만들어진다 — 신규 뼈대 생성 비용의 방어선이다. */
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<TopicResolutionView>> reject(@PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.ok(resolveTopicInputUseCase.rejectCandidates(requestId)));
    }
}
