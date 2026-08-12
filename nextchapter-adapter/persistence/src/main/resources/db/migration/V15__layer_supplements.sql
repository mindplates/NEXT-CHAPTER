-- ---------------------------------------------------------------------------
-- V15 — 개인화 보충 블록 (사용자 레이어)
--
-- 개인화는 별도 챕터를 덧붙이는 방식이 아니라 공용 뼈대에 대한 변형으로 일어난다. 그 변형의
-- 하나가 이것이다 — 막힌 지점 **뒤에** 설명·예시를 끼운다.
--
-- 본문은 건드리지 않는다. 본문을 사용자마다 다시 쓰면 여러 사람이 각자 다른 텍스트를 본 것이
-- 되어 "같은 지점에서 틀렸다"를 비교할 수 없고, 그 비교가 이 제품의 핵심 메커니즘이다. 그래서
-- 이 테이블에는 앵커와 새 블록만 있고 기존 블록을 가리켜 바꾸는 컬럼이 없다.
-- ---------------------------------------------------------------------------
CREATE TABLE layer_supplement_blocks (
    -- 이 PK 가 곧 블록 ID 의 원천이다 (s{id}). 별도 카운터를 두지 않는 이유는 재사용을 원천에서
    -- 막기 위해서다 — 순번을 계산해 발급하면 지운 뒤 다시 만들 때 지워진 ID 가 되살아나고,
    -- 그러면 두 설명의 신호가 한 블록에 섞인다.
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id),
    chapter_id      BIGINT       NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    -- 어느 블록 뒤에 끼울지. 공용 본문 블록 ID 다.
    anchor_block_id VARCHAR(20)  NOT NULL,
    -- 생성 당시 챕터 버전. 앵커가 사라졌는지 판단하는 근거이고, 나중에 "언제 만든 설명인가"를
    -- 사용자에게 보여 줄 때도 쓴다.
    chapter_version INTEGER      NOT NULL,
    block_type      VARCHAR(40)  NOT NULL,
    text            TEXT,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP    NOT NULL,
    -- 같은 사람이 같은 지점에 대해 두 번 요청하면 이미 만든 것을 돌려준다. 이 UNIQUE 가 그
    -- 방어선이다 — 없으면 새로고침 한 번이 LLM 호출 한 번이고, 비용이 조회 수만큼 늘어난다.
    CONSTRAINT uq_layer_supplement UNIQUE (user_id, chapter_id, anchor_block_id)
);

-- 합성이 (사용자, 챕터)로 조회한다. 챕터 조회마다 지나는 경로라 인덱스가 필수다.
CREATE INDEX idx_layer_supplement_user_chapter ON layer_supplement_blocks (user_id, chapter_id);
