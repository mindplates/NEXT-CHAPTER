package com.mindplates.nextchapter.domain.generation.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 재시도를 소진한 생성 항목 하나. 운영자 큐의 한 줄이다.
 *
 * <p>메시지 좌표({@code topic}/{@code partition}/{@code offset})와 <b>원본 페이로드</b>를 함께 담는다.
 * 페이로드가 있어야 운영자가 그 항목만 다시 돌릴 수 있다 — 뼈대 ID 만 남기면 할 수 있는 것이 "처음부터
 * 다시"뿐이고, 그건 이미 성공한 챕터의 생성비를 다시 쓰는 일이다.
 *
 * @param skeletonId null 이면 <b>페이로드를 읽지 못한</b> 경우다. 그때도 큐에는 올린다 — 읽을 수 없는
 *     메시지가 조용히 사라지는 것이 이 큐를 만든 이유 그 자체다. 대신 뼈대를 특정할 수 없으므로 뼈대
 *     상태는 실패로 표시하지 못한다
 * @param chapterId 본문·자산 단계만 채워진다. 그래프·개요 실패는 뼈대 단위다
 * @param attempts 소진한 시도 횟수. 몇 번 만에 포기했는지가 재시도 상한을 조정하는 근거다
 */
public record GenerationFailure(
        Long id,
        Long skeletonId,
        Long chapterId,
        GenerationStage stage,
        String topic,
        int partition,
        long offset,
        String payload,
        String errorType,
        String errorMessage,
        int attempts,
        GenerationFailureStatus status,
        String resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 에러 메시지 길이 상한. 스택 트레이스가 통째로 실려 오면 큐 화면이 읽을 수 없게 된다. */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    public GenerationFailure {
        if (stage == null || status == null) {
            throw new IllegalArgumentException("단계와 상태는 필수입니다.");
        }
        topic = Strings.requireText(topic, "토픽");
        payload = Strings.requireText(payload, "페이로드");
        errorType = Strings.requireText(errorType, "오류 종류");
        errorMessage = truncate(Strings.trimToNull(errorMessage));
        if (attempts < 1) {
            throw new IllegalArgumentException("시도 횟수는 1 이상이어야 합니다.");
        }
        if (partition < 0 || offset < 0) {
            throw new IllegalArgumentException("메시지 좌표는 0 이상이어야 합니다.");
        }
    }

    public static GenerationFailure open(
            Long skeletonId,
            Long chapterId,
            GenerationStage stage,
            String topic,
            int partition,
            long offset,
            String payload,
            String errorType,
            String errorMessage,
            int attempts) {
        return new GenerationFailure(
                null,
                skeletonId,
                chapterId,
                stage,
                topic,
                partition,
                offset,
                payload,
                errorType,
                errorMessage,
                attempts,
                GenerationFailureStatus.OPEN,
                null,
                null,
                null,
                null);
    }

    /**
     * 다시 발행했다. <b>성공으로 닫지 않는다</b> — 결과는 아직 모르고, 또 실패하면 새 항목이 들어온다.
     * 닫아 버리면 같은 원인이 몇 번 반복됐는지 알 수 없다.
     */
    public GenerationFailure retried(String actor) {
        return withStatus(GenerationFailureStatus.RETRIED, actor, null);
    }

    public GenerationFailure resolved(String actor, LocalDateTime at) {
        return withStatus(GenerationFailureStatus.RESOLVED, actor, at);
    }

    private GenerationFailure withStatus(GenerationFailureStatus next, String actor, LocalDateTime at) {
        if (status.isClosed()) {
            throw new IllegalStateException("이미 닫힌 항목입니다.");
        }
        return new GenerationFailure(
                id,
                skeletonId,
                chapterId,
                stage,
                topic,
                partition,
                offset,
                payload,
                errorType,
                errorMessage,
                attempts,
                next,
                Strings.trimToNull(actor),
                at,
                createdAt,
                updatedAt);
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
