package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GetGenerationProgressUseCase;
import com.mindplates.nextchapter.application.generation.port.in.StartSkeletonGenerationUseCase;
import com.mindplates.nextchapter.application.generation.port.out.GenerationEventPublisherPort;
import com.mindplates.nextchapter.application.generation.view.GenerationProgressView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.port.out.SaveSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잡 상태 머신과 토픽 체인의 이음새.
 *
 * <p>단계 전환이 전부 이곳을 지난다. 상태 전이와 다음 이벤트 발행이 나뉘면 파이프라인이 조용히 멈춘다 —
 * 상태만 올라가면 아무도 다음 단계를 시작하지 않고, 이벤트만 나가면 진행률이 거짓이 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SkeletonGenerationService
        implements StartSkeletonGenerationUseCase, GetGenerationProgressUseCase, AdvanceGenerationStageUseCase {

    private static final Logger log = LoggerFactory.getLogger(SkeletonGenerationService.class);

    private final LoadSkeletonPort loadSkeletonPort;
    private final SaveSkeletonPort saveSkeletonPort;
    private final LoadChapterPort loadChapterPort;
    private final GenerationEventPublisherPort eventPublisher;

    /**
     * 뼈대가 이미 있으면 만들지 않는다. 같은 주제로 두 번 요청하는 것은 합류이지 재생성이 아니고,
     * 재생성이 되면 신호가 붙어 있는 본문이 사라진다.
     */
    @Override
    public GenerationProgressView start(Long topicId) {
        return loadSkeletonPort
                .findByTopicId(topicId)
                .map(existing -> {
                    log.info("[생성] 이미 뼈대가 있어 합류한다 topicId={} status={}", topicId, existing.status());
                    return progressOf(existing);
                })
                .orElseGet(() -> {
                    Skeleton created = saveSkeletonPort.save(Skeleton.start(topicId));
                    eventPublisher.requestGraph(created.id(), topicId);
                    log.info("[생성] 뼈대 생성을 시작한다 skeletonId={} topicId={}", created.id(), topicId);
                    return progressOf(created);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationProgressView bySkeletonId(Long skeletonId) {
        return progressOf(
                loadSkeletonPort.findById(skeletonId).orElseThrow(() -> new EntityNotFoundException("뼈대", skeletonId)));
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationProgressView byTopicId(Long topicId) {
        return progressOf(loadSkeletonPort
                .findByTopicId(topicId)
                .orElseThrow(() -> new EntityNotFoundException("주제의 뼈대", topicId)));
    }

    @Override
    public void graphCompleted(Long skeletonId) {
        Skeleton advanced = advance(skeletonId, SkeletonStatus.GENERATING_OUTLINES);
        eventPublisher.requestOutlines(advanced.id());
    }

    /**
     * 챕터 수만큼 본문 생성을 발행한다. 파티션이 병렬도를 정하므로 여기서는 개수를 줄이지 않는다 —
     * 발행 측에서 조절하면 파티션 수를 늘려도 병렬도가 따라오지 않는다.
     */
    @Override
    public void outlinesCompleted(Long skeletonId) {
        Skeleton advanced = advance(skeletonId, SkeletonStatus.GENERATING_BODIES);
        List<Chapter> chapters = loadChapterPort.findBySkeletonId(skeletonId);
        if (chapters.isEmpty()) {
            // 개요 단계가 챕터를 하나도 만들지 못한 상태로 본문 단계에 들어가면 완료 판정이 즉시
            // 참이 되어 빈 뼈대가 published 로 간다. 그건 부분 공개보다 나쁘다.
            failed(skeletonId, "개요 단계 이후 챕터가 없습니다.");
            return;
        }
        chapters.forEach(chapter -> eventPublisher.requestBody(skeletonId, chapter.id(), chapter.chapterKey()));
        log.info("[생성] 본문 생성 발행 skeletonId={} chapters={}", advanced.id(), chapters.size());
    }

    /**
     * 전 챕터가 끝났을 때만 ④ 로 넘어간다.
     *
     * <p>이 메서드는 챕터마다 호출되므로 여러 컨슈머가 동시에 들어온다. 마지막 두 챕터가 같은 순간에
     * 끝나면 둘 다 "전부 끝났다"고 판정할 수 있는데, 그때 두 번째 전이는 상태 머신이 거절한다 —
     * {@code GENERATING_ASSETS → GENERATING_ASSETS} 는 허용되지 않는 전이다. 그래서 중복 전이가
     * 예외로 드러나고 조용히 두 번 진행되지 않는다.
     */
    @Override
    public void chapterBodyCompleted(Long skeletonId) {
        long remaining = loadChapterPort.countBySkeletonIdWithoutBody(skeletonId);
        if (remaining > 0) {
            log.debug("[생성] 본문 대기 {}개 남음 skeletonId={}", remaining, skeletonId);
            return;
        }
        Skeleton skeleton =
                loadSkeletonPort.findById(skeletonId).orElseThrow(() -> new EntityNotFoundException("뼈대", skeletonId));
        if (skeleton.status() != SkeletonStatus.GENERATING_BODIES) {
            log.debug("[생성] 이미 다음 단계로 넘어갔다 skeletonId={} status={}", skeletonId, skeleton.status());
            return;
        }
        saveSkeletonPort.save(skeleton.transitionTo(SkeletonStatus.GENERATING_ASSETS));
        log.info("[생성] 전 챕터 본문 완료 → 파생 자산 단계 skeletonId={}", skeletonId);
    }

    @Override
    public void failed(Long skeletonId, String reason) {
        Skeleton skeleton =
                loadSkeletonPort.findById(skeletonId).orElseThrow(() -> new EntityNotFoundException("뼈대", skeletonId));
        if (skeleton.status() == SkeletonStatus.FAILED) {
            return;
        }
        saveSkeletonPort.save(skeleton.fail());
        log.error("[생성] 뼈대 생성 실패 skeletonId={} status={} 사유={}", skeletonId, skeleton.status(), reason);
    }

    private Skeleton advance(Long skeletonId, SkeletonStatus expectedNext) {
        Skeleton skeleton =
                loadSkeletonPort.findById(skeletonId).orElseThrow(() -> new EntityNotFoundException("뼈대", skeletonId));
        if (skeleton.status() == expectedNext) {
            // 같은 단계 완료 메시지를 두 번 받았다. Kafka 는 at-least-once 이므로 정상 상황이다.
            log.debug("[생성] 이미 {} 단계다. 중복 메시지로 보고 넘어간다 skeletonId={}", expectedNext, skeletonId);
            return skeleton;
        }
        return saveSkeletonPort.save(skeleton.transitionTo(expectedNext));
    }

    private GenerationProgressView progressOf(Skeleton skeleton) {
        int total = (int) loadChapterPort.countBySkeletonId(skeleton.id());
        int withoutBody = (int) loadChapterPort.countBySkeletonIdWithoutBody(skeleton.id());
        return GenerationProgressView.of(
                skeleton.id(), skeleton.topicId(), skeleton.status(), total, total - withoutBody);
    }
}
