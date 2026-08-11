package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.ManageGenerationFailureUseCase;
import com.mindplates.nextchapter.application.generation.port.in.RecordGenerationFailureUseCase;
import com.mindplates.nextchapter.application.generation.port.out.GenerationRetryPublisherPort;
import com.mindplates.nextchapter.application.generation.port.out.LoadGenerationFailurePort;
import com.mindplates.nextchapter.application.generation.port.out.SaveGenerationFailurePort;
import com.mindplates.nextchapter.application.generation.view.GenerationFailureView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.port.out.SaveSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.common.time.AppTime;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailure;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 큐 — 재시도를 소진한 항목을 받고, 운영자가 그 항목만 다시 돌린다.
 *
 * <p>큐가 필요한 이유는 생성 실패가 <b>조용히 멈추는</b> 종류이기 때문이다. 컨슈머가 예외를 올리고
 * 재시도가 끝나면 메시지는 사라지고, 뼈대는 생성 중 상태로 남고, 아무도 그 사실을 모른다. 사용자는
 * 완료 알림을 기다린다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GenerationFailureService implements RecordGenerationFailureUseCase, ManageGenerationFailureUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerationFailureService.class);

    private final SaveGenerationFailurePort saveGenerationFailurePort;
    private final LoadGenerationFailurePort loadGenerationFailurePort;
    private final GenerationRetryPublisherPort retryPublisher;
    private final LoadSkeletonPort loadSkeletonPort;
    private final SaveSkeletonPort saveSkeletonPort;
    private final AdvanceGenerationStageUseCase advanceGenerationStageUseCase;

    /**
     * 큐에 올리고 뼈대를 실패로 표시한다. <b>순서가 중요하다</b> — 큐 기록이 먼저다.
     *
     * <p>뼈대를 먼저 실패로 바꾸면 그 사이 운영자가 화면을 보고 "실패했는데 왜 큐에 아무것도 없나"를
     * 겪는다. 반대 순서에서는 큐에 항목이 있는데 뼈대가 생성 중으로 보이는 짧은 구간이 생기는데,
     * 그쪽이 덜 나쁘다 — 무엇이 실패했는지가 이미 화면에 있다.
     */
    @Override
    public void record(GenerationFailureCommand command) {
        GenerationFailure saved = saveGenerationFailurePort.save(GenerationFailure.open(
                command.skeletonId(),
                command.chapterId(),
                command.stage(),
                command.topic(),
                command.partition(),
                command.offset(),
                command.payload(),
                command.errorType(),
                command.errorMessage(),
                command.attempts()));

        log.error(
                "[생성실패] 재시도 소진 skeletonId={} chapterId={} stage={} attempts={} error={} failureId={}",
                command.skeletonId(),
                command.chapterId(),
                command.stage(),
                command.attempts(),
                command.errorType(),
                saved.id());

        if (command.skeletonId() == null) {
            // 페이로드를 읽지 못한 메시지다. 어느 뼈대인지 알 수 없어 실패 표시를 할 수 없다 —
            // 큐에 올리는 것까지가 여기서 할 수 있는 전부이고, 그게 없으면 메시지가 조용히 사라진다.
            return;
        }
        advanceGenerationStageUseCase.failed(command.skeletonId(), command.stage() + " 단계 실패: " + command.errorType());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationFailureView> list(GenerationFailureStatus status) {
        return loadGenerationFailurePort.findByStatus(status).stream()
                .map(GenerationFailureView::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationFailureView> bySkeletonId(Long skeletonId) {
        return loadGenerationFailurePort.findBySkeletonId(skeletonId).stream()
                .map(GenerationFailureView::from)
                .toList();
    }

    /**
     * 저장된 페이로드를 그대로 다시 발행한다. 페이로드를 다시 만들지 않는 이유는 그 사이 바뀐 데이터가
     * 섞여 <b>다른 요청</b>이 되기 때문이다 — 원래 실패의 재시도가 아니게 된다.
     *
     * <p>발행 전에 뼈대 상태를 그 단계로 되돌린다. {@code FAILED} 인 채로 두면 컨슈머가 일을 끝내고 부르는
     * 단계 전환이 거절되고, 생성은 성공했는데 파이프라인이 그 자리에서 멈춘다.
     */
    @Override
    public GenerationFailureView retry(Long id, String actor) {
        GenerationFailure failure = require(id);
        if (failure.status().isClosed()) {
            throw new InvalidOperationException("이미 닫힌 항목은 다시 돌릴 수 없습니다.");
        }
        restoreSkeletonStage(failure);
        retryPublisher.republish(failure.stage(), failure.payload(), failure.skeletonId(), failure.chapterId());
        GenerationFailure retried = saveGenerationFailurePort.save(failure.retried(actor));
        log.info(
                "[생성실패] 재발행 failureId={} skeletonId={} stage={} actor={}",
                id,
                failure.skeletonId(),
                failure.stage(),
                actor);
        return GenerationFailureView.from(retried);
    }

    @Override
    public GenerationFailureView resolve(Long id, String actor) {
        GenerationFailure failure = require(id);
        if (failure.status().isClosed()) {
            return GenerationFailureView.from(failure);
        }
        return GenerationFailureView.from(
                saveGenerationFailurePort.save(failure.resolved(actor, LocalDateTime.now(AppTime.KST))));
    }

    private void restoreSkeletonStage(GenerationFailure failure) {
        if (failure.skeletonId() == null) {
            throw new InvalidOperationException("뼈대를 알 수 없는 항목은 다시 돌릴 수 없습니다.");
        }
        Skeleton skeleton = loadSkeletonPort
                .findById(failure.skeletonId())
                .orElseThrow(() -> new EntityNotFoundException("뼈대", failure.skeletonId()));
        SkeletonStatus target = failure.stage().skeletonStatus();
        if (skeleton.status() == target) {
            return;
        }
        if (!skeleton.status().canTransitionTo(target)) {
            throw new InvalidOperationException("이 뼈대는 " + target + " 단계로 되돌릴 수 없습니다: 현재 " + skeleton.status());
        }
        saveSkeletonPort.save(skeleton.transitionTo(target));
    }

    private GenerationFailure require(Long id) {
        return loadGenerationFailurePort.findById(id).orElseThrow(() -> new EntityNotFoundException("생성 실패 항목", id));
    }
}
