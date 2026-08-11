-- ---------------------------------------------------------------------------
-- V8 — 챕터 개요와 중복·누락 소견
--
-- ② 개요 생성 단계를 따로 둔 대가는 호출 횟수와 대기시간이다. 그걸 감수하는 이유는
-- 챕터 간 중복·누락을 본문 생성 **전에** 잡는 편이 훨씬 싸기 때문이다 — 본문을 다 쓴 뒤
-- 겹친다는 것을 알면 그 챕터의 본문 생성비와 파생 자산비가 모두 버려진다.
--
-- 개요를 chapters 컬럼이 아니라 별도 테이블에 두는 이유는 읽는 쪽이 다르기 때문이다.
-- 챕터 목록·그래프 조회는 개요를 필요로 하지 않고(트리 조회는 자주 일어난다), 개요를
-- 필요로 하는 것은 본문 생성과 검수뿐이다.
-- ---------------------------------------------------------------------------
CREATE TABLE chapter_outlines (
    chapter_id BIGINT    PRIMARY KEY REFERENCES chapters (id) ON DELETE CASCADE,
    -- 본문이 다뤄야 할 항목 목록. 순서가 의미를 가지므로 배열로 둔다.
    points     JSONB     NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 중복·누락 소견 -------------------------------------------------------------
-- 소견은 **본문 생성을 막지 않는다.** 겹침 판정은 어림이고, 오탐 하나가 파이프라인을
-- 세우면 사전 생성의 이점이 사라진다. 대신 기록으로 남겨 운영자 검수가 보게 한다.
--
-- 막는 것은 하나뿐이다 — 개요가 비어 있는 챕터. 그건 본문을 쓸 재료가 없다는 뜻이라
-- 어림이 아니라 사실이고, 그대로 두면 빈 본문이 생성된다.
CREATE TABLE chapter_outline_findings (
    id           BIGSERIAL   PRIMARY KEY,
    skeleton_id  BIGINT      NOT NULL REFERENCES skeletons (id) ON DELETE CASCADE,
    finding_type VARCHAR(40) NOT NULL,
    -- 관련 챕터 키(쉼표 구분). 조인·집계 대상이 아니라 사람이 읽는 근거다.
    chapter_keys VARCHAR(500),
    detail       TEXT        NOT NULL,
    created_at   TIMESTAMP   NOT NULL
);
CREATE INDEX idx_outline_findings_skeleton ON chapter_outline_findings (skeleton_id);
