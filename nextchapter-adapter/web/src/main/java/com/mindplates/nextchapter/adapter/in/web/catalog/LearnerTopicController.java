package com.mindplates.nextchapter.adapter.in.web.catalog;

import com.mindplates.nextchapter.adapter.in.web.catalog.dto.ConfirmTopicCandidateRequest;
import com.mindplates.nextchapter.adapter.in.web.catalog.dto.ResolveTopicInputRequest;
import com.mindplates.nextchapter.adapter.in.web.support.AuthenticatedUser;
import com.mindplates.nextchapter.application.catalog.port.in.LearnerTopicEntryUseCase;
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
 * 사용자의 주제 입력. M1 에서 관리자 경로에만 있던 것이 여기서 열린다.
 *
 * <p>M1 이 이 경로를 미룬 이유가 둘이었고 둘 다 해소됐다 — 소셜 로그인(P3.1)으로 호출자를 특정할 수
 * 있게 됐고, 레이트 리밋이 붙었다. 호출 하나가 LLM 을 최대 3번 부르므로 그 둘 없이 열면 비용 남용
 * 경로가 그대로 생긴다.
 *
 * <p>{@code requestId} 의 소유권은 유스케이스가 확인한다. 없으면 다른 사용자의 대기 중인 요청을 확정할
 * 수 있고, 그건 남의 입력을 엉뚱한 주제로 수렴시키면서 신규 뼈대 생성까지 대신 트리거하는 것이다.
 */
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class LearnerTopicController {

    private final LearnerTopicEntryUseCase learnerTopicEntryUseCase;

    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<TopicResolutionView>> resolve(
            @RequestBody @Valid ResolveTopicInputRequest request, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                learnerTopicEntryUseCase.resolve(AuthenticatedUser.requireId(principal), request.input())));
    }

    @PostMapping("/resolve/{requestId}/confirm")
    public ResponseEntity<ApiResponse<TopicResolutionView>> confirm(
            @PathVariable Long requestId,
            @RequestBody @Valid ConfirmTopicCandidateRequest request,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(learnerTopicEntryUseCase.confirmCandidate(
                AuthenticatedUser.requireId(principal), requestId, request.topicId())));
    }

    /** 후보 전부 거절. 붙일 분야가 있으면 신규 주제와 뼈대가 만들어진다 — 생성 비용의 방어선이다. */
    @PostMapping("/resolve/{requestId}/reject")
    public ResponseEntity<ApiResponse<TopicResolutionView>> reject(@PathVariable Long requestId, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                learnerTopicEntryUseCase.rejectCandidates(AuthenticatedUser.requireId(principal), requestId)));
    }
}
