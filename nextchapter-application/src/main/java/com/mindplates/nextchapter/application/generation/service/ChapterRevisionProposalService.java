package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.admin.port.in.GenerateRevisionProposalsUseCase;
import com.mindplates.nextchapter.application.admin.port.out.LoadRevisionProposalPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveRevisionProposalPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.signal.port.out.LoadCollectiveSignalAggregatePort;
import com.mindplates.nextchapter.application.signal.port.out.LoadRevisionTriggerPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveRevisionTriggerPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.RevisionProposal;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import com.mindplates.nextchapter.domain.signal.model.FormatBreakdown;
import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P5.3 수정안 생성 — 트리거 하나를 AI 수정안 + 근거로 바꾼다.
 *
 * <p>반영(챕터 버전 증가)은 하지 않는다. 여기서 만드는 것은 <b>제안</b>뿐이고, 실행은 승인 대기열(P5.4)을
 * 지나 {@code RecordChapterBodyUseCase} 가 한다 — 본문 기록 경로가 하나여야 하기 때문이다.
 *
 * <p>실패한 트리거 하나가 뒤의 전부를 막지 않게 건별로 격리한다. 표시하지 않은 채 넘어가므로 다음
 * 폴링이 다시 시도한다 — {@link com.mindplates.nextchapter.application.signal.service.SignalPublishService}
 * 와 같은 정책이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChapterRevisionProposalService implements GenerateRevisionProposalsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChapterRevisionProposalService.class);

    /** 블록 문서 하나는 수 KB 다. 짧으면 뒤쪽 블록이 잘려 quiz·narration 이 빠진다. */
    private static final int MAX_TOKENS = 16384;

    private final LoadRevisionTriggerPort loadRevisionTriggerPort;
    private final SaveRevisionTriggerPort saveRevisionTriggerPort;
    private final LoadRevisionProposalPort loadRevisionProposalPort;
    private final SaveRevisionProposalPort saveRevisionProposalPort;
    private final LoadCollectiveSignalAggregatePort loadCollectiveSignalAggregatePort;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterVersionPort loadChapterVersionPort;
    private final AiGateway aiGateway;

    @Override
    public int generatePending(int limit) {
        List<RevisionTrigger> triggers = loadRevisionTriggerPort.claimUnprocessed(limit);
        int generated = 0;
        for (RevisionTrigger trigger : triggers) {
            try {
                generateFor(trigger);
                generated++;
            } catch (RuntimeException e) {
                log.error(
                        "[수정안] 생성 실패, 다음 폴링에서 다시 시도한다 triggerId={} chapterId={} blockId={}: {}",
                        trigger.id(),
                        trigger.chapterId(),
                        trigger.blockId(),
                        e.getMessage());
            }
        }
        if (generated > 0) {
            log.info("[수정안] {}건 생성", generated);
        }
        return generated;
    }

    private void generateFor(RevisionTrigger trigger) {
        if (loadRevisionProposalPort.findByTriggerId(trigger.id()).isPresent()) {
            saveRevisionTriggerPort.markProcessed(trigger.id());
            return;
        }

        Chapter chapter = loadChapterPort
                .findById(trigger.chapterId())
                .orElseThrow(() -> new EntityNotFoundException("챕터", trigger.chapterId()));

        // 트리거 이후 챕터가 이미 다른 경로(운영자 수정·검증 패스)로 바뀌었다면, 이 트리거는 더 이상
        // 최신 본문에 대한 것이 아니다 — 낡은 근거로 제안을 만들면 그 사이의 변경을 덮어쓰게 된다.
        if (chapter.currentVersion() != trigger.chapterVersion()) {
            log.info(
                    "[수정안] 이미 다른 버전으로 바뀌어 건너뛴다 chapterId={} triggerVersion={} currentVersion={}",
                    trigger.chapterId(),
                    trigger.chapterVersion(),
                    chapter.currentVersion());
            saveRevisionTriggerPort.markProcessed(trigger.id());
            return;
        }

        ChapterVersion current = loadChapterVersionPort
                .find(trigger.chapterId(), trigger.chapterVersion())
                .orElseThrow(() ->
                        new EntityNotFoundException("챕터 버전", trigger.chapterId() + ":v" + trigger.chapterVersion()));

        List<FormatBreakdown> breakdown = loadCollectiveSignalAggregatePort.formatBreakdown(
                trigger.chapterId(), trigger.chapterVersion(), trigger.blockId());

        String userPrompt = ChapterRevisionPrompts.prompt(
                chapter.title(),
                current.body().blocks(),
                trigger.blockId(),
                trigger.questionCount(),
                trigger.attemptCount(),
                trigger.wrongCount(),
                breakdown);

        String responseText = aiGateway
                .complete(
                        AiStage.SKELETON_BODY,
                        chapter.skeletonId(),
                        ChapterRevisionPrompts.SYSTEM_PROMPT,
                        userPrompt,
                        MAX_TOKENS)
                .text();

        List<ProposedBlock> proposedBlocks = ChapterBodyPrompts.parse(responseText);
        String rationale = ChapterRevisionPrompts.rationale(responseText);

        RevisionProposal proposal = ChapterBodyPrompts.hasAnySource(proposedBlocks)
                ? RevisionProposal.verified(
                        trigger.id(),
                        trigger.chapterId(),
                        trigger.chapterVersion(),
                        trigger.blockId(),
                        rationale,
                        breakdown,
                        proposedBlocks)
                : RevisionProposal.verificationFailed(
                        trigger.id(),
                        trigger.chapterId(),
                        trigger.chapterVersion(),
                        trigger.blockId(),
                        rationale,
                        breakdown,
                        proposedBlocks,
                        "제안된 본문에 출처가 하나도 없습니다.");

        saveRevisionProposalPort.save(proposal);
        saveRevisionTriggerPort.markProcessed(trigger.id());
        log.info(
                "[수정안] 생성 완료 triggerId={} chapterId={} blockId={} status={}",
                trigger.id(),
                trigger.chapterId(),
                trigger.blockId(),
                proposal.status());
    }
}
