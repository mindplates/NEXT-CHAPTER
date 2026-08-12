package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;

/**
 * 사용자의 주제 진입. {@link ResolveTopicInputUseCase} 를 감싸 <b>레이트 리밋과 소유권</b>을 더한다.
 *
 * <p>운영자 경로와 나눈 이유가 그 둘이다. 운영자는 카탈로그를 채우려고 같은 API 를 반복해서 부르므로
 * 사용자와 같은 한도를 걸 수 없고, 요청 토큰({@code requestId})의 소유권 검사는 운영자에게는 의미가
 * 없다 — 운영자는 남의 입력을 다루는 것이 일이다.
 *
 * <p>소유권 검사가 없으면 <b>다른 사용자의 대기 중인 요청을 확정할 수 있다.</b> 그건 그 사람의 입력이
 * 엉뚱한 주제로 수렴하는 것이고, 신규 뼈대 생성까지 대신 트리거할 수 있다.
 */
public interface LearnerTopicEntryUseCase {

    TopicResolutionView resolve(Long userId, String rawInput);

    TopicResolutionView confirmCandidate(Long userId, Long requestId, Long topicId);

    TopicResolutionView rejectCandidates(Long userId, Long requestId);
}
