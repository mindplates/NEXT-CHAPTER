package com.mindplates.nextchapter.adapter.in.web.generation;

import com.mindplates.nextchapter.application.chapter.port.in.GetSkeletonGraphUseCase;
import com.mindplates.nextchapter.application.chapter.view.SkeletonGraphView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 그래프 검수. 생성된 구조를 눈으로 확인할 수 없으면 M2 를 검증할 방법이 테스트뿐이다. */
@RestController
@RequestMapping("/api/admin/skeletons")
@RequiredArgsConstructor
public class SkeletonGraphAdminController {

    private final GetSkeletonGraphUseCase getSkeletonGraphUseCase;

    @GetMapping("/{skeletonId}/graph")
    public ResponseEntity<ApiResponse<SkeletonGraphView>> graph(@PathVariable Long skeletonId) {
        return ResponseEntity.ok(ApiResponse.ok(getSkeletonGraphUseCase.bySkeletonId(skeletonId)));
    }
}
