-- ---------------------------------------------------------------------------
-- V20 — 수정안 (M5 P5.3)
--
-- AI가 생성한 수정안 + 근거를 담는다. 반영(챕터 버전 증가)은 여기서 하지 않는다 — 이 테이블은
-- 제안이고, 실행은 승인 대기열(P5.4)을 지나 챕터 본문 기록의 단일 경로가 한다.
--
-- trigger_id 에 UNIQUE 를 건다. 트리거 하나가 제안 하나를 만든다는 불변식을 애플리케이션 검사가
-- 아니라 DB 가 지킨다 — 동시 폴링 두 건이 같은 트리거를 집어도 한쪽만 성공한다.
-- ---------------------------------------------------------------------------
CREATE TABLE revision_proposals (
    id                BIGSERIAL   PRIMARY KEY,
    trigger_id        BIGINT      NOT NULL REFERENCES revision_triggers (id) ON DELETE CASCADE,
    chapter_id        BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    -- 이 제안이 만들어질 때 기준으로 삼은 챕터 버전. 승인 시점에 챕터의 현재 버전과 다르면
    -- 그 사이 다른 경로로 바뀐 것이므로 이 제안을 그대로 반영해선 안 된다(P5.4).
    chapter_version   INTEGER     NOT NULL,
    block_id          VARCHAR(20) NOT NULL,
    rationale         TEXT        NOT NULL,
    -- 딜리버리 형태별 분해. 임계치 판정에는 쓰지 않았고 여기서만 근거로 쓴다.
    format_breakdown  JSONB       NOT NULL,
    -- ProposedBlock 목록. ID 가 확정되지 않은 채로 담긴다 — 확정(승계)은 반영 시점에
    -- RecordChapterBodyUseCase 가 한다.
    proposed_blocks   JSONB       NOT NULL,
    status            VARCHAR(30) NOT NULL,
    verification_note TEXT,
    created_at        TIMESTAMP   NOT NULL,
    decided_at        TIMESTAMP,
    decided_by        VARCHAR(200),
    CONSTRAINT uq_revision_proposal_trigger UNIQUE (trigger_id)
);

-- 승인 대기열 목록 조회가 상태로 좁혀 읽는다.
CREATE INDEX idx_revision_proposals_status ON revision_proposals (status);
CREATE INDEX idx_revision_proposals_chapter ON revision_proposals (chapter_id);
