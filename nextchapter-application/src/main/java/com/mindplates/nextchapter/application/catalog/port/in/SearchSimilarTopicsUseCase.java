package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import java.util.List;

public interface SearchSimilarTopicsUseCase {

    /**
     * 자유 입력과 의미가 가까운 주제를 찾는다.
     *
     * <p>P1.5 의 미매칭 경로가 이것으로 후보를 만든다 — 사용자에게 "이 주제 아닌가요?"를 물을 수
     * 있어야 신규 뼈대 생성이 마지막 수단이 된다.
     */
    List<TopicSimilarityView> search(String freeText, int limit);
}
