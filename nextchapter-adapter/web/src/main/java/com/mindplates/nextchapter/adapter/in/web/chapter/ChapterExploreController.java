package com.mindplates.nextchapter.adapter.in.web.chapter;

import com.mindplates.nextchapter.application.chapter.port.in.ExploreChapterGraphUseCase;
import com.mindplates.nextchapter.application.chapter.view.ChapterNeighborhoodView;
import com.mindplates.nextchapter.application.chapter.view.SkeletonEntryView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자의 그래프 탐색. 진입점을 받고, 거기서 이웃을 따라간다.
 *
 * <p>검수용 그래프 조회({@code /api/admin/skeletons/{id}/graph})와 나눈 이유는 답하는 대상이 다르기
 * 때문이다. 운영자는 생성 중인 뼈대 전체를 보고, 사용자는 published 뼈대의 <b>주변만</b> 본다. 한
 * 엔드포인트에 조건을 얹으면 "권한에 따라 다른 것을 주는" API 가 되고, 그건 캐시도 테스트도 어렵다.
 */
@RestController
@RequiredArgsConstructor
public class ChapterExploreController {

    /** 기본 홉 수. 1홉은 화면이 너무 좁고, 3홉은 뼈대 전체에 가까워진다. */
    private static final int DEFAULT_DEPTH = 2;

    private final ExploreChapterGraphUseCase exploreChapterGraphUseCase;

    /** 주제로 진입. 사용자 경로가 주제 입력에서 시작하므로 뼈대 ID 를 몰라도 들어올 수 있어야 한다. */
    @GetMapping("/api/topics/{topicId}/entry-points")
    public ResponseEntity<ApiResponse<SkeletonEntryView>> entryPointsByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.ok(exploreChapterGraphUseCase.entryPointsByTopicId(topicId)));
    }

    @GetMapping("/api/skeletons/{skeletonId}/entry-points")
    public ResponseEntity<ApiResponse<SkeletonEntryView>> entryPoints(@PathVariable Long skeletonId) {
        return ResponseEntity.ok(ApiResponse.ok(exploreChapterGraphUseCase.entryPointsBySkeletonId(skeletonId)));
    }

    @GetMapping("/api/chapters/{chapterId}/neighbors")
    public ResponseEntity<ApiResponse<ChapterNeighborhoodView>> neighbors(
            @PathVariable Long chapterId, @RequestParam(defaultValue = "" + DEFAULT_DEPTH) int depth) {
        return ResponseEntity.ok(ApiResponse.ok(exploreChapterGraphUseCase.neighbors(chapterId, depth)));
    }
}
