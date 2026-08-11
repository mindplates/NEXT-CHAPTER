package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.ComposeChapterBodyUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GetAiBatchJobUseCase;
import com.mindplates.nextchapter.application.generation.port.in.PollAiBatchUseCase;
import com.mindplates.nextchapter.application.generation.port.in.SubmitChapterBodyBatchUseCase;
import com.mindplates.nextchapter.application.generation.port.out.GenerationEventPublisherPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItem;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItemResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchPollResult;
import com.mindplates.nextchapter.application.generation.port.out.LoadAiBatchJobPort;
import com.mindplates.nextchapter.application.generation.port.out.SaveAiBatchJobPort;
import com.mindplates.nextchapter.application.generation.view.AiBatchJobView;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import com.mindplates.nextchapter.common.time.AppTime;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.generation.model.AiBatchJob;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ③ 본문 생성의 배치 경로.
 *
 * <p>비동기 일괄 생성이므로 배치 조건에 부합한다 — <b>전 토큰 50% 할인.</b> 비동기 생성 결정이 그대로 비용
 * 절반으로 돌아온다. 대가는 최대 24시간의 추가 지연이고, 그건 이미 감수한 대가다(첫 소비까지 수십 분~시간).
 *
 * <p><b>실패는 개별 경로로 넘긴다.</b> 항목 하나가 실패하면 그 챕터의 본문 생성 메시지를 발행한다. 배치용
 * 재시도 정책을 따로 만들지 않는 이유는 그것이 두 번째 실패 처리 체계가 되기 때문이다 — P2.8 의 재시도와
 * 운영자 큐가 이미 있고, 둘이 어긋나면 어떤 실패는 아무도 재시도하지 않는다.
 *
 * <p><b>반영 경로에 클래스 레벨 트랜잭션을 두지 않는다.</b> 항목 하나의 반영이 실패했을 때 그 실패를 삼키고
 * 다음 항목으로 넘어가려면 각 반영이 자기 트랜잭션이어야 한다. 한 트랜잭션 안에서 삼키면 예외가 트랜잭션을
 * 롤백 전용으로 표시해 <b>커밋 시점에 전체가 되돌아간다</b> — 성공한 항목까지 사라지는데, 그 실패는 개별
 * 항목의 로그만 보면 드러나지 않는다.
 */
@Service
public class ChapterBodyBatchService
        implements SubmitChapterBodyBatchUseCase, PollAiBatchUseCase, GetAiBatchJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChapterBodyBatchService.class);

    private final LoadChapterPort loadChapterPort;
    private final ComposeChapterBodyUseCase composeChapterBodyUseCase;
    private final AiGateway aiGateway;
    private final SaveAiBatchJobPort saveAiBatchJobPort;
    private final LoadAiBatchJobPort loadAiBatchJobPort;
    private final GenerationEventPublisherPort eventPublisher;
    private final AdvanceGenerationStageUseCase advanceGenerationStageUseCase;
    private final boolean batchEnabled;

    /**
     * 배치를 켜는 것이 설정값인 이유는 <b>지연이 개발을 막기 때문이다.</b> 배치는 완료까지 최대 24시간이므로
     * 로컬에서 파이프라인을 한 바퀴 돌려 보는 동안에는 개별 호출이 필요하다. 비용이 문제가 되는 것은 운영이고,
     * 그때 켠다. 기본값을 끔으로 둔 것은 켜져 있는 편이 <b>기다림으로 나타나기</b> 때문이다 — 꺼져 있으면
     * 비용이 더 들지만 즉시 돌고, 켜져 있으면 하루를 기다린 뒤에야 뭔가 잘못됐음을 안다.
     */
    public ChapterBodyBatchService(
            LoadChapterPort loadChapterPort,
            ComposeChapterBodyUseCase composeChapterBodyUseCase,
            AiGateway aiGateway,
            SaveAiBatchJobPort saveAiBatchJobPort,
            LoadAiBatchJobPort loadAiBatchJobPort,
            GenerationEventPublisherPort eventPublisher,
            AdvanceGenerationStageUseCase advanceGenerationStageUseCase,
            @Value("${nextchapter.generation.batch.enabled:false}") boolean batchEnabled) {
        this.loadChapterPort = loadChapterPort;
        this.composeChapterBodyUseCase = composeChapterBodyUseCase;
        this.aiGateway = aiGateway;
        this.saveAiBatchJobPort = saveAiBatchJobPort;
        this.loadAiBatchJobPort = loadAiBatchJobPort;
        this.eventPublisher = eventPublisher;
        this.advanceGenerationStageUseCase = advanceGenerationStageUseCase;
        this.batchEnabled = batchEnabled;
    }

    /**
     * 본문 생성을 배치 한 건으로 제출한다.
     *
     * <p><b>false 를 돌려주면 호출부가 개별 호출 경로로 간다.</b> 조용히 아무것도 하지 않으면 그 뼈대의 본문이
     * 영원히 생성되지 않고, 뼈대는 본문 생성 단계에 갇힌다.
     */
    @Override
    @Transactional
    public boolean submitBodies(Long skeletonId) {
        if (!batchEnabled) {
            return false;
        }
        if (!aiGateway.supportsBatch(AiStage.SKELETON_BODY)) {
            // 능력 조회 결과를 로그로 남긴다. 배치를 켜 뒀는데 개별 호출로 돌고 있는 상태를 모르면
            // "할인이 적용되고 있다"고 착각한 채 정가를 낸다.
            log.warn("[배치] 본문 단계에 지정된 벤더가 배치를 지원하지 않아 개별 호출로 진행한다 skeletonId={}", skeletonId);
            return false;
        }

        List<LlmBatchItem> items = batchItems(skeletonId);
        if (items.isEmpty()) {
            log.info("[배치] 제출할 챕터가 없다 — 전부 본문이 있다 skeletonId={}", skeletonId);
            return false;
        }

        try {
            AiGateway.BatchSubmission submission = aiGateway.submitBatch(AiStage.SKELETON_BODY, skeletonId, items);
            saveAiBatchJobPort.save(AiBatchJob.submitted(
                    skeletonId,
                    GenerationStage.BODY,
                    submission.vendor(),
                    submission.model(),
                    submission.batchId(),
                    items.size()));
            log.info("[배치] 본문 {}개를 배치로 제출했다 skeletonId={} batchId={}", items.size(), skeletonId, submission.batchId());
            return true;
        } catch (BudgetExceededException exception) {
            // 예산 초과는 재시도로 풀리지 않는다. 개별 호출로 내려가도 같은 상한에 막히므로 그 자리에서
            // 실패로 확정한다 — 개별 경로로 넘기면 챕터 수만큼 같은 실패를 반복한다.
            advanceGenerationStageUseCase.failed(skeletonId, "예산 상한 초과: " + exception.getMessage());
            return true;
        }
    }

    private List<LlmBatchItem> batchItems(Long skeletonId) {
        List<LlmBatchItem> items = new ArrayList<>();
        for (Chapter chapter : loadChapterPort.findBySkeletonId(skeletonId)) {
            composeChapterBodyUseCase
                    .promptFor(skeletonId, chapter.id())
                    .ifPresent(prompt -> items.add(new LlmBatchItem(
                            String.valueOf(chapter.id()),
                            prompt.systemPrompt(),
                            prompt.userPrompt(),
                            prompt.maxTokens())));
        }
        return items;
    }

    /**
     * 끝난 배치를 반영한다.
     *
     * <p>배치마다 독립적으로 처리한다. 하나가 실패해도 나머지는 반영된다 — 한 배치의 조회 실패로 전체 스윕이
     * 멈추면 다른 뼈대의 본문이 함께 밀린다.
     */
    @Override
    public int pollSubmitted() {
        int applied = 0;
        for (AiBatchJob job : loadAiBatchJobPort.findSubmitted()) {
            try {
                if (applyIfEnded(job)) {
                    applied++;
                }
            } catch (RuntimeException exception) {
                log.error(
                        "[배치] 반영 실패 batchId={} skeletonId={}: {}",
                        job.batchId(),
                        job.skeletonId(),
                        exception.getMessage());
            }
        }
        return applied;
    }

    private boolean applyIfEnded(AiBatchJob job) {
        LlmBatchPollResult result =
                aiGateway.pollBatch(AiStage.SKELETON_BODY, job.skeletonId(), job.vendor(), job.model(), job.batchId());
        if (!result.ended()) {
            return false;
        }

        Map<Long, Chapter> chaptersById = chaptersById(job.skeletonId());
        Set<Long> fallbacks = new HashSet<>();
        int succeeded = 0;

        for (LlmBatchItemResult item : result.results()) {
            Long chapterId = chapterIdOf(item.customId());
            if (chapterId == null || !chaptersById.containsKey(chapterId)) {
                // 응답의 customId 가 이 뼈대의 챕터가 아니다. 개별 경로로 넘길 대상이 없으므로 기록만 한다.
                log.error("[배치] 알 수 없는 customId={} batchId={}", item.customId(), job.batchId());
                continue;
            }
            if (applyItem(job, chapterId, item)) {
                succeeded++;
            } else {
                fallbacks.add(chapterId);
            }
        }

        // 결과에 아예 오지 않은 챕터도 개별 경로로 넘긴다. 벤더가 항목을 누락하면 그 챕터는 성공도 실패도
        // 아닌 상태가 되고, 그대로 두면 뼈대가 본문 생성 단계에 갇힌다. 본문 유무는 반영 후 상태로 다시
        // 읽는다 — 반영 전 스냅샷으로 판정하면 방금 채운 챕터를 빠진 것으로 본다.
        chaptersById(job.skeletonId()).values().stream()
                .filter(chapter -> !chapter.hasBody())
                .map(Chapter::id)
                .forEach(fallbacks::add);

        fallbacks.forEach(chapterId -> eventPublisher.requestBody(
                job.skeletonId(), chapterId, chaptersById.get(chapterId).chapterKey()));

        saveAiBatchJobPort.save(job.completed(LocalDateTime.now(AppTime.KST)));
        log.info(
                "[배치] 반영 완료 batchId={} 성공={} 개별경로={} items={}",
                job.batchId(),
                succeeded,
                fallbacks.size(),
                job.itemCount());

        if (succeeded > 0) {
            // 완료 보고가 전 챕터 완료 판정을 트리거한다. 남은 챕터가 있으면 그쪽에서 no-op 이 된다.
            advanceGenerationStageUseCase.chapterBodyCompleted(job.skeletonId());
        }
        return true;
    }

    /**
     * 항목 하나를 반영한다.
     *
     * @return 반영했으면 true. false 면 개별 경로로 넘긴다 — 실패했거나 응답이 파싱되지 않았다
     */
    private boolean applyItem(AiBatchJob job, Long chapterId, LlmBatchItemResult item) {
        if (!item.succeeded()) {
            log.warn("[배치] 항목 실패 chapterId={} batchId={} 사유={}", chapterId, job.batchId(), item.errorMessage());
            return false;
        }
        try {
            composeChapterBodyUseCase.apply(chapterId, item.text());
            return true;
        } catch (RuntimeException exception) {
            // 파싱 실패·출처 없음. 배치 결과를 재시도할 수는 없으므로 개별 호출로 다시 만든다.
            log.warn("[배치] 항목 반영 실패 chapterId={}: {}", chapterId, exception.getMessage());
            return false;
        }
    }

    private Map<Long, Chapter> chaptersById(Long skeletonId) {
        return loadChapterPort.findBySkeletonId(skeletonId).stream()
                .collect(Collectors.toMap(Chapter::id, Function.identity()));
    }

    private static Long chapterIdOf(String customId) {
        try {
            return Long.valueOf(customId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiBatchJobView> bySkeletonId(Long skeletonId) {
        return loadAiBatchJobPort.findBySkeletonId(skeletonId).stream()
                .map(AiBatchJobView::from)
                .toList();
    }
}
