package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.LearnerTopicEntryUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ResolveTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.port.out.TopicResolveRateLimitPort;
import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.RateLimitExceededException;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 사용자의 주제 진입. 레이트 리밋과 소유권을 얹고 해소 로직은 그대로 위임한다.
 *
 * <p>해소 로직을 복제하지 않는 것이 요점이다. 사용자 경로와 운영자 경로가 각자 수렴 규칙을 갖게 되면
 * 같은 입력이 경로에 따라 다른 뼈대로 가고, 그 순간 주제 통합이 깨진다 — 신호가 갈리는 바로 그 실패다.
 */
@Service
@RequiredArgsConstructor
public class LearnerTopicEntryService implements LearnerTopicEntryUseCase {

    private static final Logger log = LoggerFactory.getLogger(LearnerTopicEntryService.class);

    private final ResolveTopicInputUseCase resolveTopicInputUseCase;
    private final LoadTopicInputLogPort loadTopicInputLogPort;
    private final TopicResolveRateLimitPort topicResolveRateLimitPort;

    @Override
    public TopicResolutionView resolve(Long userId, String rawInput) {
        if (!topicResolveRateLimitPort.tryConsume(userId)) {
            log.warn("[주제 진입] 레이트 리밋 초과 userId={}", userId);
            throw new RateLimitExceededException(
                    "주제 입력이 너무 잦습니다. %d초 후에 다시 시도해 주세요.".formatted(topicResolveRateLimitPort.windowSeconds()));
        }
        return resolveTopicInputUseCase.resolve(rawInput, actor(userId));
    }

    /**
     * 확정·거절에는 리밋을 걸지 않는다. 모델을 부르지 않고, 요청 토큰 하나당 한 번만 유효하다 —
     * 이미 소비된 토큰은 해소 서비스가 거절한다.
     */
    @Override
    public TopicResolutionView confirmCandidate(Long userId, Long requestId, Long topicId) {
        requireOwnership(userId, requestId);
        return resolveTopicInputUseCase.confirmCandidate(requestId, topicId);
    }

    @Override
    public TopicResolutionView rejectCandidates(Long userId, Long requestId) {
        requireOwnership(userId, requestId);
        return resolveTopicInputUseCase.rejectCandidates(requestId);
    }

    /**
     * 남의 요청 토큰을 다루지 못하게 한다.
     *
     * <p>없는 것과 남의 것을 <b>같은 404 로</b> 접는다. 나누면 응답만으로 어떤 requestId 가 존재하는지
     * 열거할 수 있고, 그 목록은 다른 사람의 주제 입력이다.
     */
    private void requireOwnership(Long userId, Long requestId) {
        TopicInputLog entry = loadTopicInputLogPort
                .findById(requestId)
                .filter(candidate -> actor(userId).equals(candidate.userId()))
                .orElseThrow(() -> new EntityNotFoundException("주제 입력 요청", requestId));
        if (!entry.awaitsUserDecision()) {
            throw new EntityNotFoundException("주제 입력 요청", requestId);
        }
    }

    /** 입력 로그의 {@code userId} 는 문자열이다 — 운영자는 이메일, 사용자는 ID 가 들어온다. */
    private static String actor(Long userId) {
        return userId == null ? null : String.valueOf(userId);
    }
}
