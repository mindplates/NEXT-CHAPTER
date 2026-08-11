package com.mindplates.nextchapter.domain.catalog.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주제 하나의 임베딩. 주제당 하나다.
 *
 * <p>{@code model} 을 값으로 들고 있는 것이 이 타입의 핵심이다. 모델이 다른 벡터 간 코사인 유사도는
 * 비교 자체가 무의미한데 <b>에러가 나지 않는다</b> — 조용히 틀린 주제 매핑이 나오고, 그건 신호를
 * 엉뚱한 뼈대에 붙여 개선 루프의 전제를 무너뜨린다. 모델을 함께 저장해 두면 "지금 모델로 만든
 * 벡터만 비교한다"는 규칙을 쿼리로 표현할 수 있다.
 *
 * @param sourceText 임베딩의 원천 텍스트. 재임베딩이 같은 입력으로 다시 계산할 수 있어야 하고,
 *     매칭이 빗나갔을 때 무엇을 비교했는지 볼 수 있어야 한다
 */
public record TopicEmbedding(
        Long topicId,
        String model,
        int dimension,
        String sourceText,
        float[] vector,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public TopicEmbedding {
        if (topicId == null) {
            throw new IllegalArgumentException("임베딩은 주제에 속해야 합니다.");
        }
        model = Strings.requireText(model, "임베딩 모델");
        sourceText = Strings.requireText(sourceText, "임베딩 원천 텍스트");
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("임베딩 벡터가 비어 있습니다.");
        }
        if (dimension != vector.length) {
            throw new IllegalArgumentException("차원(" + dimension + ")이 벡터 길이(" + vector.length + ")와 다릅니다.");
        }
        vector = vector.clone();
    }

    public static TopicEmbedding of(Long topicId, String model, String sourceText, float[] vector) {
        return new TopicEmbedding(topicId, model, vector == null ? 0 : vector.length, sourceText, vector, null, null);
    }

    /**
     * 임베딩할 텍스트를 조립한다. 이름만으로는 "머신러닝"과 "기계학습"이 표기 거리로만 비교되므로
     * 별칭과 설명을 함께 넣어 의미 공간을 넓힌다 — 별칭 대조(정확 일치)가 실패한 자유 입력을
     * 잡아내는 것이 임베딩의 역할이다.
     */
    public static String sourceTextOf(CatalogTopic topic, List<TopicAlias> aliases) {
        StringBuilder text = new StringBuilder(topic.name());
        aliases.stream().map(TopicAlias::alias).forEach(alias -> text.append(' ')
                .append(alias));
        if (topic.description() != null) {
            text.append(' ').append(topic.description());
        }
        return text.toString();
    }

    /** 배열은 복제해 돌려준다 — 레코드가 준 참조를 호출자가 고치면 저장된 값이 바뀐다. */
    @Override
    public float[] vector() {
        return vector.clone();
    }
}
