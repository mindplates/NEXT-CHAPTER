package com.mindplates.nextchapter.adapter.in.web.layer;

import com.mindplates.nextchapter.adapter.in.web.support.AuthenticatedUser;
import com.mindplates.nextchapter.application.layer.port.in.GetChapterLayerUseCase;
import com.mindplates.nextchapter.application.layer.view.ChapterUnderstandingView;
import com.mindplates.nextchapter.application.layer.view.SkeletonLayerView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 학습 상태.
 *
 * <p>경로에 사용자 ID 가 없다 — 언제나 <b>토큰의 주인</b>이다. 경로에 두면 남의 레이어를 조회할 수 있는
 * 경로가 생기고, 그건 다른 사람의 오답과 질문을 읽는 것이다.
 */
@RestController
@RequiredArgsConstructor
public class LearnerLayerController {

    private final GetChapterLayerUseCase getChapterLayerUseCase;

    @GetMapping("/api/skeletons/{skeletonId}/my-layer")
    public ResponseEntity<ApiResponse<SkeletonLayerView>> skeletonLayer(
            @PathVariable Long skeletonId, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                getChapterLayerUseCase.bySkeletonId(AuthenticatedUser.requireId(principal), skeletonId)));
    }

    @GetMapping("/api/chapters/{chapterId}/my-layer")
    public ResponseEntity<ApiResponse<ChapterUnderstandingView>> chapterLayer(
            @PathVariable Long chapterId, Principal principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(getChapterLayerUseCase.byChapterId(AuthenticatedUser.requireId(principal), chapterId)));
    }
}
