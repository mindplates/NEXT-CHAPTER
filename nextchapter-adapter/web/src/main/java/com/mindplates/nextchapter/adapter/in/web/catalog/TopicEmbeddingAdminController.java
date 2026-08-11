package com.mindplates.nextchapter.adapter.in.web.catalog;

import com.mindplates.nextchapter.application.catalog.port.in.EmbedTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ReembedTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.SearchSimilarTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.view.EmbeddingStatusView;
import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 임베딩 현황과 재임베딩 배치.
 *
 * <p>재임베딩을 배치 단위 반복 호출로 노출하는 이유는 임베딩이 외부 호출이고 주제 수만큼 비용이
 * 들기 때문이다. 응답의 {@code outdated} 가 0이 될 때까지 부르는 형태로 쓴다 — 중간에 끊겨도 다음
 * 호출이 남은 것부터 이어간다.
 *
 * <p>모델 교체 시 <b>자동으로</b> 이 배치가 도는 것은 M2 에서 Kafka 가 들어온 뒤다. 지금은 관리
 * 화면이 현황을 보고 실행한다 — 요청 스레드에서 전량을 돌리면 수천 건의 외부 호출이 한 요청에
 * 묶인다.
 */
@RestController
@RequestMapping("/api/admin/catalog/embeddings")
@RequiredArgsConstructor
public class TopicEmbeddingAdminController {

    /** 한 요청이 만들 외부 호출 수의 상한. 요청 하나가 벤더 레이트리밋을 통째로 먹지 않게 한다. */
    private static final int MAX_BATCH_SIZE = 100;

    private final ReembedTopicsUseCase reembedTopicsUseCase;
    private final EmbedTopicUseCase embedTopicUseCase;
    private final SearchSimilarTopicsUseCase searchSimilarTopicsUseCase;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<EmbeddingStatusView>> status() {
        return ResponseEntity.ok(ApiResponse.ok(reembedTopicsUseCase.status()));
    }

    @PostMapping("/reembed")
    public ResponseEntity<ApiResponse<EmbeddingStatusView>> reembed(@RequestParam(defaultValue = "20") int batchSize) {
        return ResponseEntity.ok(
                ApiResponse.ok(reembedTopicsUseCase.reembedBatch(Math.min(batchSize, MAX_BATCH_SIZE))));
    }

    @PostMapping("/topics/{topicId}")
    public ResponseEntity<ApiResponse<EmbeddingStatusView>> embedOne(@PathVariable Long topicId) {
        embedTopicUseCase.embed(topicId);
        return ResponseEntity.ok(ApiResponse.ok(reembedTopicsUseCase.status()));
    }

    /** 운영자가 컷오프·후보 품질을 눈으로 확인하는 경로. 사용자 매핑 경로는 P1.5 에서 따로 붙는다. */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TopicSimilarityView>>> search(
            @RequestParam String query, @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(searchSimilarTopicsUseCase.search(query, limit)));
    }
}
