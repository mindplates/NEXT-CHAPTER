package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;

/**
 * 자유 입력을 정확히 하나의 뼈대로 수렴시킨다.
 *
 * <p>세 단계로 나뉘어 있는 것이 설계의 핵심이다. {@link #resolve} 는 <b>절대 신규 주제를 만들지
 * 않는다</b> — 만들 수 있는 것은 사용자가 후보를 모두 거절한 {@link #rejectCandidates} 뿐이다.
 * 자동 생성으로 흘리면 오매칭 방지망이 없고, 운영자 승인 대기열로 보내면 사용자가 그 자리에서
 * 학습을 시작할 수 없다. 사용자를 통합의 마지막 판정자로 세우는 것이 그 사이의 답이다.
 */
public interface ResolveTopicInputUseCase {

    /** 별칭 → LLM 단계적 매핑 → 유사도 후보. 매칭되면 기존 뼈대에 합류한다. */
    TopicResolutionView resolve(String rawInput, String userId);

    /** 제시된 후보 중 하나를 사용자가 골랐다. */
    TopicResolutionView confirmCandidate(Long requestId, Long topicId);

    /** 후보를 모두 거절했다. 붙일 분야가 있으면 신규 주제를 만들고, 없으면 운영자 검토로 넘긴다. */
    TopicResolutionView rejectCandidates(Long requestId);
}
