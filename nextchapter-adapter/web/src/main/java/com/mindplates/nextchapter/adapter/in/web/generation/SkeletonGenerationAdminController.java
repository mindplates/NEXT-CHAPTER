package com.mindplates.nextchapter.adapter.in.web.generation;

import com.mindplates.nextchapter.application.generation.port.in.GetAiBatchJobUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GetGenerationProgressUseCase;
import com.mindplates.nextchapter.application.generation.port.in.StartSkeletonGenerationUseCase;
import com.mindplates.nextchapter.application.generation.view.AiBatchJobView;
import com.mindplates.nextchapter.application.generation.view.GenerationProgressView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 뼈대 사전 생성과 진행률 조회.
 *
 * <p>사전 생성은 초기 카탈로그를 채우는 운영자 수단이다 — 사용자의 주제 입력을 대체하지 않는다.
 *
 * <p>진행률 조회가 필요한 이유는 첫 소비까지 수십 분~시간 단위가 걸리기 때문이다. 사용자를 로딩
 * 화면에 붙잡아두지 않는 대신 "어디까지 됐는지"를 볼 수 있어야 하고, 완료 알림도 이 값을 재료로 쓴다.
 */
@RestController
@RequestMapping("/api/admin/skeletons")
@RequiredArgsConstructor
public class SkeletonGenerationAdminController {

    private final StartSkeletonGenerationUseCase startSkeletonGenerationUseCase;
    private final GetGenerationProgressUseCase getGenerationProgressUseCase;
    private final GetAiBatchJobUseCase getAiBatchJobUseCase;

    /** 멱등이다 — 이미 뼈대가 있으면 만들지 않고 진행 상태를 돌려준다. */
    @PostMapping("/topics/{topicId}")
    public ResponseEntity<ApiResponse<GenerationProgressView>> start(@PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.ok(startSkeletonGenerationUseCase.start(topicId)));
    }

    @GetMapping("/{skeletonId}/progress")
    public ResponseEntity<ApiResponse<GenerationProgressView>> progress(@PathVariable Long skeletonId) {
        return ResponseEntity.ok(ApiResponse.ok(getGenerationProgressUseCase.bySkeletonId(skeletonId)));
    }

    /**
     * 이 뼈대의 배치 잡. 진행률만 보면 <b>멈춘 것과 배치를 기다리는 것이 구분되지 않는다</b> — 배치는 최대
     * 24시간 아무 변화 없이 살아 있다.
     */
    @GetMapping("/{skeletonId}/batches")
    public ResponseEntity<ApiResponse<List<AiBatchJobView>>> batches(@PathVariable Long skeletonId) {
        return ResponseEntity.ok(ApiResponse.ok(getAiBatchJobUseCase.bySkeletonId(skeletonId)));
    }

    @GetMapping("/topics/{topicId}/progress")
    public ResponseEntity<ApiResponse<GenerationProgressView>> progressByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.ok(getGenerationProgressUseCase.byTopicId(topicId)));
    }
}
