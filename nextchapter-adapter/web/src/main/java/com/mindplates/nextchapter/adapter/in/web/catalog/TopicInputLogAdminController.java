package com.mindplates.nextchapter.adapter.in.web.catalog;

import com.mindplates.nextchapter.adapter.in.web.catalog.dto.ReviewTopicInputRequest;
import com.mindplates.nextchapter.application.catalog.port.in.ReviewTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.view.TopicInputLogView;
import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.common.response.ApiResponse;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미매핑 입력 검토 (P1.6).
 *
 * <p>기본 조회가 미검토 + {@code NEEDS_REVIEW} 인 이유는 그것이 실제 대기열이기 때문이다. 매칭에
 * 성공한 입력도 같은 테이블에 있지만 그건 품질 확인용 기록이고, 운영자가 손댈 일이 없다.
 */
@RestController
@RequestMapping("/api/admin/catalog/input-logs")
@RequiredArgsConstructor
public class TopicInputLogAdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewTopicInputUseCase reviewTopicInputUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<TopicInputLogView>>> list(
            @RequestParam(required = false) List<TopicInputResolution> resolution,
            @RequestParam(defaultValue = "true") boolean onlyUnreviewed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<TopicInputResolution> filter =
                resolution == null || resolution.isEmpty() ? List.of(TopicInputResolution.NEEDS_REVIEW) : resolution;
        return ResponseEntity.ok(ApiResponse.ok(
                reviewTopicInputUseCase.list(filter, onlyUnreviewed, page, Math.min(size, MAX_PAGE_SIZE))));
    }

    /** 전체 기록. 매칭 품질(별칭 vs LLM 비율, 오매칭 여부)을 보는 경로다. */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<PagedResult<TopicInputLogView>>> listAll(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(reviewTopicInputUseCase.list(List.of(), false, page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<TopicInputLogView>> review(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewTopicInputRequest request,
            Principal principal) {
        String reviewer = principal == null ? null : principal.getName();
        String note = request == null ? null : request.note();
        return ResponseEntity.ok(ApiResponse.ok(reviewTopicInputUseCase.markReviewed(id, reviewer, note)));
    }
}
