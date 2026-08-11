package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.domain.catalog.model.TopicEmbedding;
import java.util.List;
import java.util.Optional;

public interface LoadTopicEmbeddingPort {

    Optional<TopicEmbedding> findByTopicId(Long topicId);

    /** 지금 모델로 만들어진 벡터를 가진 주제 수. */
    long countByModel(String model);

    /**
     * 벡터가 없거나 다른 모델로 만들어진 주제 ID. 재임베딩 배치의 작업 목록이다.
     *
     * @param limit 한 번에 처리할 개수. 전량을 한 트랜잭션에 넣지 않는 이유는 임베딩이 외부 호출이고
     *     주제 수만큼 비용이 들기 때문이다 — 중간에 끊겨도 다음 호출이 남은 것부터 이어야 한다
     */
    List<Long> findTopicIdsNotEmbeddedWith(String model, int limit);

    /** 재임베딩이 남은 주제 수. */
    long countTopicsNotEmbeddedWith(String model);

    /**
     * 코사인 유사도 상위 주제.
     *
     * <p>{@code model} 로 반드시 걸러야 한다. 모델이 다른 벡터와 비교하면 값이 나오긴 하는데 의미가
     * 없고, 에러도 나지 않는다. 걸러 두면 재임베딩이 끝나지 않은 주제는 <b>안 보이는</b> 쪽으로
     * 실패한다 — 조용히 틀린 결과보다 낫다.
     */
    List<TopicSimilarityView> searchSimilar(float[] queryVector, String model, int limit, double minScore);
}
