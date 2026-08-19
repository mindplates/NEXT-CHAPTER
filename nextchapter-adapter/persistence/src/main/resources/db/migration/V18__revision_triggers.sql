-- ---------------------------------------------------------------------------
-- V18 — 수정 트리거 (M5 P5.2)
--
-- 블록 하나가 임계치를 넘은 사례를 남긴다. 이 테이블에 행이 하나라도 존재한다는 사실이 3개월
-- 성공 기준의 필수 항목이다 — "사용자 신호가 집계되어 뼈대 수정을 트리거한 사례가 존재한다."
--
-- (chapter_id, chapter_version, block_id) 에 UNIQUE 를 걸어 같은 버전의 같은 블록은 한 번만
-- 트리거되게 한다. 신호가 계속 쌓여도 트리거는 한 번으로 충분하다 — 이미 큐에 올랐거나 처리된
-- 항목을 다시 올리는 것은 의미가 없다. 다음 트리거는 챕터가 수정되어 버전이 올라간 뒤에나 가능하다.
-- ---------------------------------------------------------------------------
CREATE TABLE revision_triggers (
    id              BIGSERIAL   PRIMARY KEY,
    chapter_id      BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    chapter_version INTEGER     NOT NULL,
    block_id        VARCHAR(20) NOT NULL,
    -- 트리거 시점의 집계 스냅샷. P5.3 이 수정안의 근거로 그대로 쓴다.
    question_count  BIGINT      NOT NULL,
    attempt_count   BIGINT      NOT NULL,
    wrong_count     BIGINT      NOT NULL,
    triggered_at    TIMESTAMP   NOT NULL,
    CONSTRAINT uq_revision_trigger UNIQUE (chapter_id, chapter_version, block_id)
);

CREATE INDEX idx_revision_triggers_chapter ON revision_triggers (chapter_id);
