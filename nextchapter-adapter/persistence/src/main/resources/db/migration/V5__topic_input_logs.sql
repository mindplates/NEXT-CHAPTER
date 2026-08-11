-- ---------------------------------------------------------------------------
-- V5 — 주제 입력 로그
--
-- 두 가지를 동시에 맡는다.
--   1) P1.5 의 대화 상태 — "후보를 제시했다"와 "사용자가 무엇을 골랐다" 사이를 잇는다.
--      별도 세션 저장소를 두지 않는 이유는, 이 상태가 어차피 남겨야 하는 기록이기
--      때문이다. 후보 제시 → 거절 → 신규 등록의 판단 근거가 로그 그 자체다.
--   2) P1.6 의 검토 대기열 — 매칭에 실패한 입력이 카탈로그의 빈 곳을 가리킨다.
--
-- 한 테이블인 이유가 여기 있다. 대화 상태를 휘발성으로 두면 "사용자가 후보를 모두
-- 거절했다"는 사실이 사라지고, 그건 신규 뼈대가 생성된 유일한 근거다.
-- ---------------------------------------------------------------------------
CREATE TABLE topic_input_logs (
    id                  BIGSERIAL    PRIMARY KEY,
    raw_input           VARCHAR(500) NOT NULL,
    -- 정규화 값. 같은 표현이 몇 번 들어왔는지 세는 데 쓴다 — 반복되는 미매핑 입력이
    -- 카탈로그에 무엇이 빠졌는지를 가장 직접적으로 알려 준다.
    normalized          VARCHAR(500),
    resolution          VARCHAR(40)  NOT NULL,
    match_source        VARCHAR(40),
    matched_topic_id    BIGINT       REFERENCES catalog_topics (id) ON DELETE SET NULL,
    -- LLM 이 고른 분야. 사용자가 후보를 모두 거절했을 때 신규 주제를 붙일 자리다.
    -- 여기가 NULL 이면 붙일 자리가 없다는 뜻이고, 그 입력은 운영자 검토로 간다.
    proposed_field_id   BIGINT       REFERENCES catalog_fields (id) ON DELETE SET NULL,
    proposed_topic_name VARCHAR(160),
    -- LLM 이 제안한 slug. 한글 주제명에서는 ASCII slug 를 만들 수 없어 모델 제안을 남겨 둔다.
    proposed_topic_slug VARCHAR(120),
    candidate_topic_ids VARCHAR(200),
    user_id             VARCHAR(200),
    reviewed_at         TIMESTAMP,
    reviewed_by         VARCHAR(200),
    review_note         TEXT,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL
);

-- 검토 대기열 조회 — 미검토 + 특정 상태.
CREATE INDEX idx_topic_input_logs_resolution ON topic_input_logs (resolution, reviewed_at);
-- 같은 표현이 몇 번 들어왔는지.
CREATE INDEX idx_topic_input_logs_normalized ON topic_input_logs (normalized);
