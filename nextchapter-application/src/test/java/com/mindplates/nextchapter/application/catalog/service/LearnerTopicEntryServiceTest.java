package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.in.ResolveTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.port.out.TopicResolveRateLimitPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.RateLimitExceededException;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 사용자 경로가 운영자 경로와 다른 점 <b>두 가지만</b> 확인한다 — 레이트 리밋과 소유권. 해소 로직은
 * 위임하므로 여기서 다시 검증하지 않는다(복제하면 같은 입력이 경로에 따라 다른 뼈대로 간다).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 주제 진입")
class LearnerTopicEntryServiceTest {

    @Mock
    ResolveTopicInputUseCase resolveTopicInputUseCase;

    @Mock
    LoadTopicInputLogPort loadTopicInputLogPort;

    @Mock
    TopicResolveRateLimitPort topicResolveRateLimitPort;

    @InjectMocks
    LearnerTopicEntryService service;

    private static TopicInputLog pending(String userId) {
        return new TopicInputLog(
                11L,
                "머신러닝",
                null,
                com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution.CANDIDATES_PRESENTED,
                null,
                null,
                3L,
                "머신러닝",
                "machine-learning",
                List.of(7L),
                userId,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("리밋 안이면 해소로 위임한다 — 사용자 ID 가 입력 로그에 남는다")
    void delegatesWhenUnderLimit() {
        when(topicResolveRateLimitPort.tryConsume(42L)).thenReturn(true);

        service.resolve(42L, "머신러닝");

        verify(resolveTopicInputUseCase).resolve("머신러닝", "42");
    }

    /** 이 호출 하나가 LLM 을 최대 3번 부른다. 막지 못하면 그 비용이 예산 상한으로 흘러 생성을 멈춘다. */
    @Test
    @DisplayName("리밋을 넘으면 모델을 부르지 않는다")
    void blocksOverLimit() {
        when(topicResolveRateLimitPort.tryConsume(42L)).thenReturn(false);
        when(topicResolveRateLimitPort.windowSeconds()).thenReturn(600);

        assertThatThrownBy(() -> service.resolve(42L, "머신러닝"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("600");
        verify(resolveTopicInputUseCase, never()).resolve(any(), any());
    }

    /** 남의 요청 토큰을 확정할 수 있으면 그 사람의 입력이 엉뚱한 주제로 수렴하고 뼈대까지 만들어진다. */
    @Test
    @DisplayName("남의 요청은 확정할 수 없다")
    void rejectsOtherUsersRequest() {
        when(loadTopicInputLogPort.findById(11L)).thenReturn(Optional.of(pending("999")));

        assertThatThrownBy(() -> service.confirmCandidate(42L, 11L, 7L)).isInstanceOf(EntityNotFoundException.class);
        verify(resolveTopicInputUseCase, never()).confirmCandidate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("내 요청은 확정된다")
    void confirmsOwnRequest() {
        when(loadTopicInputLogPort.findById(11L)).thenReturn(Optional.of(pending("42")));

        service.confirmCandidate(42L, 11L, 7L);

        verify(resolveTopicInputUseCase).confirmCandidate(11L, 7L);
    }

    @Test
    @DisplayName("내 요청은 거절할 수 있다")
    void rejectsOwnRequest() {
        when(loadTopicInputLogPort.findById(11L)).thenReturn(Optional.of(pending("42")));

        service.rejectCandidates(42L, 11L);

        verify(resolveTopicInputUseCase).rejectCandidates(11L);
    }

    /** 이미 판정이 끝난 토큰을 다시 쓰면 같은 입력으로 두 번 뼈대를 만들 수 있다. */
    @Test
    @DisplayName("이미 판정된 요청은 다시 쓸 수 없다")
    void rejectsAlreadyDecided() {
        when(loadTopicInputLogPort.findById(11L))
                .thenReturn(Optional.of(pending("42").resolvedByUser(7L)));

        assertThatThrownBy(() -> service.rejectCandidates(42L, 11L)).isInstanceOf(EntityNotFoundException.class);
    }

    /** 없는 것과 남의 것을 나누면 응답만으로 requestId 를 열거할 수 있고, 그 목록은 남의 주제 입력이다. */
    @Test
    @DisplayName("없는 요청도 같은 404 다")
    void missingRequestIsSameNotFound() {
        when(loadTopicInputLogPort.findById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmCandidate(42L, 11L, 7L)).isInstanceOf(EntityNotFoundException.class);
    }
}
