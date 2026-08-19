package com.mindplates.nextchapter.domain.admin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("수정안")
class RevisionProposalTest {

    private static List<ProposedBlock> blocks() {
        return List.of(ProposedBlock.text(BlockType.PARAGRAPH, "고친 설명"));
    }

    @Test
    @DisplayName("검증을 통과하면 승인 대기 상태로 만들어진다")
    void createsPendingApprovalWhenVerified() {
        RevisionProposal proposal =
                RevisionProposal.verified(1L, 100L, 2, "b6", "질문 5건이 이 블록에 몰렸다", List.of(), blocks());

        assertThat(proposal.status()).isEqualTo(RevisionProposalStatus.PENDING_APPROVAL);
        assertThat(proposal.isPendingApproval()).isTrue();
    }

    @Test
    @DisplayName("검증에 실패하면 검증 실패 상태로 만들어진다")
    void createsVerificationFailedWhenNotVerified() {
        RevisionProposal proposal = RevisionProposal.verificationFailed(
                1L, 100L, 2, "b6", "질문 5건이 이 블록에 몰렸다", List.of(), blocks(), "출처 없는 핵심 주장이 있다");

        assertThat(proposal.status()).isEqualTo(RevisionProposalStatus.VERIFICATION_FAILED);
        assertThat(proposal.isPendingApproval()).isFalse();
        assertThat(proposal.verificationNote()).isEqualTo("출처 없는 핵심 주장이 있다");
    }

    @Test
    @DisplayName("근거가 없으면 거절한다")
    void rejectsBlankRationale() {
        assertThatThrownBy(() -> RevisionProposal.verified(1L, 100L, 2, "b6", "  ", List.of(), blocks()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제안된 블록이 비어 있으면 거절한다")
    void rejectsEmptyProposedBlocks() {
        assertThatThrownBy(() -> RevisionProposal.verified(1L, 100L, 2, "b6", "근거", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보충 블록에는 붙지 않는다")
    void rejectsSupplementBlock() {
        assertThatThrownBy(() -> RevisionProposal.verified(1L, 100L, 2, "s1", "근거", List.of(), blocks()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 제안이 만들어진 뒤 챕터가 다시 바뀌었다면, 그 사이의 변경을 덮어쓰지 않도록 승인 전에 막아야 한다. */
    @Test
    @DisplayName("기준 버전과 현재 버전이 다르면 오래된 제안이다")
    void detectsStaleProposal() {
        RevisionProposal proposal = RevisionProposal.verified(1L, 100L, 2, "b6", "근거", List.of(), blocks());

        assertThat(proposal.isStaleAgainst(2)).isFalse();
        assertThat(proposal.isStaleAgainst(3)).isTrue();
    }
}
