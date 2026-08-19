package com.mindplates.nextchapter.domain.admin.model;

import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import com.mindplates.nextchapter.domain.signal.model.FormatBreakdown;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI가 생성한 수정안. {@link com.mindplates.nextchapter.domain.signal.model.RevisionTrigger} 하나가
 * 이 제안 하나를 만든다.
 *
 * <p>반영(챕터 버전 증가)은 여기서 하지 않는다. 이 레코드는 <b>제안</b>이고, 실행은 승인 대기열(P5.4)을
 * 지나 {@code RecordChapterBodyUseCase} 가 한다 — 본문 기록 경로는 하나뿐이어야 하기 때문이다.
 */
public record RevisionProposal(
        Long id,
        Long triggerId,
        Long chapterId,
        int chapterVersion,
        String blockId,
        String rationale,
        List<FormatBreakdown> formatBreakdown,
        List<ProposedBlock> proposedBlocks,
        RevisionProposalStatus status,
        String verificationNote,
        LocalDateTime createdAt,
        LocalDateTime decidedAt,
        String decidedBy) {

    public RevisionProposal {
        if (triggerId == null) {
            throw new IllegalArgumentException("수정안에는 트리거가 필요합니다.");
        }
        if (chapterId == null) {
            throw new IllegalArgumentException("수정안에는 챕터가 필요합니다.");
        }
        if (chapterVersion < 1) {
            throw new IllegalArgumentException("수정안에는 기준 버전이 필요합니다: " + chapterVersion);
        }
        if (!BlockIds.isBody(blockId)) {
            throw new IllegalArgumentException("수정안은 공용 본문 블록에만 붙습니다: " + blockId);
        }
        if (Strings.trimToNull(rationale) == null) {
            throw new IllegalArgumentException("수정안에는 근거가 필요합니다.");
        }
        if (proposedBlocks == null || proposedBlocks.isEmpty()) {
            throw new IllegalArgumentException("수정안에는 제안된 본문이 필요합니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("수정안 상태는 필수입니다.");
        }
        formatBreakdown = formatBreakdown == null ? List.of() : List.copyOf(formatBreakdown);
        proposedBlocks = List.copyOf(proposedBlocks);
        verificationNote = Strings.trimToNull(verificationNote);
        decidedBy = Strings.trimToNull(decidedBy);
    }

    /** 검증 패스를 통과한 새 제안. */
    public static RevisionProposal verified(
            Long triggerId,
            Long chapterId,
            int chapterVersion,
            String blockId,
            String rationale,
            List<FormatBreakdown> formatBreakdown,
            List<ProposedBlock> proposedBlocks) {
        return new RevisionProposal(
                null,
                triggerId,
                chapterId,
                chapterVersion,
                blockId,
                rationale,
                formatBreakdown,
                proposedBlocks,
                RevisionProposalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                null);
    }

    /** 검증 패스가 주장–출처 불일치를 찾은 제안. 승인 대기열에 노출되지 않는다. */
    public static RevisionProposal verificationFailed(
            Long triggerId,
            Long chapterId,
            int chapterVersion,
            String blockId,
            String rationale,
            List<FormatBreakdown> formatBreakdown,
            List<ProposedBlock> proposedBlocks,
            String verificationNote) {
        return new RevisionProposal(
                null,
                triggerId,
                chapterId,
                chapterVersion,
                blockId,
                rationale,
                formatBreakdown,
                proposedBlocks,
                RevisionProposalStatus.VERIFICATION_FAILED,
                verificationNote,
                null,
                null,
                null);
    }

    public boolean isPendingApproval() {
        return status == RevisionProposalStatus.PENDING_APPROVAL;
    }

    /**
     * 챕터가 이 제안이 만들어진 뒤로 다시 바뀌었는지. 바뀌었다면 이 제안은 <b>더 이상 최신 본문에 대한
     * 것이 아니다</b> — 그 위에 반영하면 그 사이의 변경을 덮어쓴다.
     */
    public boolean isStaleAgainst(int currentChapterVersion) {
        return currentChapterVersion != chapterVersion;
    }
}
