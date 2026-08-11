package com.mindplates.nextchapter.adapter.in.web.generation;

import com.mindplates.nextchapter.application.chapter.port.in.GetChapterOutlinesUseCase;
import com.mindplates.nextchapter.application.chapter.view.ChapterOutlineView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개요·소견 검수.
 *
 * <p>중복·누락 소견이 본문 생성을 막지 않으므로, 운영자가 볼 수 있는 경로가 유일한 활용 수단이다.
 * 보이지 않으면 소견을 만드는 비용만 쓰고 아무것도 고치지 못한다.
 */
@RestController
@RequestMapping("/api/admin/skeletons")
@RequiredArgsConstructor
public class SkeletonOutlineAdminController {

    private final GetChapterOutlinesUseCase getChapterOutlinesUseCase;

    @GetMapping("/{skeletonId}/outlines")
    public ResponseEntity<ApiResponse<ChapterOutlineView>> outlines(@PathVariable Long skeletonId) {
        return ResponseEntity.ok(ApiResponse.ok(getChapterOutlinesUseCase.bySkeletonId(skeletonId)));
    }
}
