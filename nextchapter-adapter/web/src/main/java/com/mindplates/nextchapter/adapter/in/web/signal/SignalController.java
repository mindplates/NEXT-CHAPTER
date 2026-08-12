package com.mindplates.nextchapter.adapter.in.web.signal;

import com.mindplates.nextchapter.adapter.in.web.signal.dto.RecordSignalRequest;
import com.mindplates.nextchapter.adapter.in.web.support.AuthenticatedUser;
import com.mindplates.nextchapter.application.signal.port.in.RecordSignalUseCase;
import com.mindplates.nextchapter.application.signal.view.SignalView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신호 기록. <b>별도 평가 UI 가 없다</b>는 원칙의 구현 지점이다 — 품질 신호는 소비 과정에서 전부
 * 발생하므로 엔드포인트도 챕터 아래에 붙는다.
 *
 * <p>종류별로 경로를 나누지 않는다(`/quiz`, `/questions`, …). 검증과 두 갈래 경로가 전부 같으므로
 * 나누면 그것이 네 곳에 복제되고, 한 곳만 빠뜨려도 그 종류의 신호가 조용히 집계에서 빠진다.
 */
@RestController
@RequestMapping("/api/chapters/{chapterId}/signals")
@RequiredArgsConstructor
public class SignalController {

    private final RecordSignalUseCase recordSignalUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<SignalView>> record(
            @PathVariable Long chapterId, @RequestBody @Valid RecordSignalRequest request, Principal principal) {
        SignalView view =
                recordSignalUseCase.record(AuthenticatedUser.requireId(principal), request.toCommand(chapterId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(view));
    }
}
