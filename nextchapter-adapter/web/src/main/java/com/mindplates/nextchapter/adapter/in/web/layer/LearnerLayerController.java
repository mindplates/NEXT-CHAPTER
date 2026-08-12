package com.mindplates.nextchapter.adapter.in.web.layer;

import com.mindplates.nextchapter.adapter.in.web.support.AuthenticatedUser;
import com.mindplates.nextchapter.application.layer.port.in.GetChapterLayerUseCase;
import com.mindplates.nextchapter.application.layer.port.in.RecommendNextChapterUseCase;
import com.mindplates.nextchapter.application.layer.port.in.RequestSupplementUseCase;
import com.mindplates.nextchapter.application.layer.view.ChapterUnderstandingView;
import com.mindplates.nextchapter.application.layer.view.NextChapterView;
import com.mindplates.nextchapter.application.layer.view.SkeletonLayerView;
import com.mindplates.nextchapter.application.layer.view.SupplementView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final RecommendNextChapterUseCase recommendNextChapterUseCase;
    private final RequestSupplementUseCase requestSupplementUseCase;

    /**
     * 막힌 지점에 보충 설명을 요청한다.
     *
     * <p>블록 경로에 붙는 이유는 이 요청이 <b>지점</b>에 대한 것이기 때문이다. 챕터 단위로 두면 "어디가
     * 막혔는지"를 본문에서 다시 추정해야 하고, 그 추정이 틀리면 엉뚱한 곳에 설명이 붙는다.
     *
     * <p>이미 만든 것이 있으면 그것을 돌려주므로 같은 요청을 반복해도 LLM 을 다시 부르지 않는다.
     */
    @PostMapping("/api/chapters/{chapterId}/blocks/{blockId}/supplement")
    public ResponseEntity<ApiResponse<SupplementView>> supplement(
            @PathVariable Long chapterId, @PathVariable String blockId, Principal principal) {
        SupplementView view =
                requestSupplementUseCase.request(AuthenticatedUser.requireId(principal), chapterId, blockId);
        return ResponseEntity.status(view.reused() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.ok(view));
    }

    /**
     * 다음에 볼 챕터. 그래프 이웃 조회({@code /neighbors})와 나눈 이유는 답이 다르기 때문이다 — 이웃은
     * <b>구조</b>이고 이것은 <b>그 사람에게 맞춘 순서</b>다. 한 엔드포인트에 섞으면 캐시할 수 있는 것과 없는
     * 것이 같은 응답에 들어간다.
     */
    @GetMapping("/api/chapters/{chapterId}/next")
    public ResponseEntity<ApiResponse<NextChapterView>> next(@PathVariable Long chapterId, Principal principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(recommendNextChapterUseCase.next(AuthenticatedUser.requireId(principal), chapterId)));
    }

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
