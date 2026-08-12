package com.mindplates.nextchapter.adapter.in.web.chapter;

import com.mindplates.nextchapter.adapter.in.web.support.AuthenticatedUser;
import com.mindplates.nextchapter.application.chapter.port.in.ComposeChapterUseCase;
import com.mindplates.nextchapter.application.chapter.view.ComposedChapterView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자의 챕터 소비.
 *
 * <p>응답은 <b>블록 목록</b>이다. 서버가 HTML 을 만들지 않으므로 웹·iOS·Android 가 같은 엔드포인트를 쓴다.
 *
 * <p>{@code format} 을 쿼리 파라미터로 받는 이유는 <b>같은 챕터를 형태별로 다르게 해석</b>하기 때문이다
 * (웹은 narration 을 그리지 않는다). 형태별로 엔드포인트를 나누면 캐시 키와 신호 기록이 경로마다 갈린다.
 */
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
public class ChapterReadController {

    private final ComposeChapterUseCase composeChapterUseCase;

    @GetMapping("/{chapterId}")
    public ResponseEntity<ApiResponse<ComposedChapterView>> chapter(
            @PathVariable Long chapterId,
            @RequestParam(defaultValue = "WEB") DeliveryFormat format,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                composeChapterUseCase.compose(AuthenticatedUser.requireId(principal), chapterId, format)));
    }
}
