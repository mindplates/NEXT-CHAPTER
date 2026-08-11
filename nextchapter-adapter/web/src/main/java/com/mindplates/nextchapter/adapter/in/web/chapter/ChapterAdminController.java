package com.mindplates.nextchapter.adapter.in.web.chapter;

import com.mindplates.nextchapter.application.chapter.port.in.GetChapterUseCase;
import com.mindplates.nextchapter.application.chapter.view.ChapterBodyView;
import com.mindplates.nextchapter.application.chapter.view.ChapterVersionSummaryView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자의 챕터 검수·이력 조회.
 *
 * <p>사용자용 소비 API 는 M3 다. 여기서 먼저 여는 이유는 검수(관리자 콘솔의 "콘텐츠 → 검수")가 생성
 * 파이프라인과 함께 필요하기 때문이다 — 생성 결과를 볼 수 없으면 M2 를 검증할 방법이 테스트뿐이다.
 *
 * <p>응답은 <b>블록 문서</b>이지 HTML 이 아니다. 렌더링은 클라이언트가 하고, 그래서 새 플랫폼이
 * 생겨도 이 API 가 그대로다.
 */
@RestController
@RequestMapping("/api/admin/chapters")
@RequiredArgsConstructor
public class ChapterAdminController {

    private final GetChapterUseCase getChapterUseCase;

    @GetMapping("/{chapterId}")
    public ResponseEntity<ApiResponse<ChapterBodyView>> current(@PathVariable Long chapterId) {
        return ResponseEntity.ok(ApiResponse.ok(getChapterUseCase.currentBody(chapterId)));
    }

    /** 임의 시점의 본문. 수정 전후 오답률 비교(3개월 성공 기준 2번)가 이것을 필요로 한다. */
    @GetMapping("/{chapterId}/versions/{version}")
    public ResponseEntity<ApiResponse<ChapterBodyView>> atVersion(
            @PathVariable Long chapterId, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.ok(getChapterUseCase.bodyAt(chapterId, version)));
    }

    @GetMapping("/{chapterId}/versions")
    public ResponseEntity<ApiResponse<List<ChapterVersionSummaryView>>> history(@PathVariable Long chapterId) {
        return ResponseEntity.ok(ApiResponse.ok(getChapterUseCase.history(chapterId)));
    }
}
