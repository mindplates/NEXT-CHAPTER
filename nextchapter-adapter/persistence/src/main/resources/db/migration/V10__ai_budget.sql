-- ---------------------------------------------------------------------------
-- V10 — AI 사용량과 예산 상한
--
-- 전 형태 사전 생성을 택했으므로 상한은 안전장치가 아니라 사실상 필수다. 범위가 넘스러운
-- 주제 하나가 예산을 혼자 태우는 것을 막고(뼈대당), 전체 폭주를 막는다(일일).
--
-- 상한의 단위가 **토큰**인 이유:
--   토큰 수는 벤더 응답에서 그대로 오는 정확한 값이지만, 단가는 외부에서 바뀌는 사실이다.
--   단가를 코드나 DB 에 박아 두면 벤더가 가격을 바꾼 순간 예산이 조용히 틀려진다 — 과하게
--   막거나 전혀 막지 못하는데, 어느 쪽도 에러가 나지 않는다. 운영자가 자기 금액 예산을
--   현재 단가로 한 번 토큰으로 환산하는 편이 낫다.
--
--   대가는 단계마다 다른 벤더를 쓰면 토큰당 단가가 달라 상한의 의미가 흐려진다는 점이다.
--   그때는 단계 설정에 단가 컬럼을 붙여 금액으로 환산한다.
-- ---------------------------------------------------------------------------
CREATE TABLE ai_usage_records (
    id            BIGSERIAL   PRIMARY KEY,
    -- NULL 은 뼈대에 귀속되지 않는 호출이다(주제 매핑·임베딩 등). 일일 상한에는 포함되고
    -- 뼈대당 상한에는 포함되지 않는다.
    skeleton_id   BIGINT,
    stage         VARCHAR(60)  NOT NULL,
    vendor        VARCHAR(40)  NOT NULL,
    model         VARCHAR(120) NOT NULL,
    input_tokens  INTEGER      NOT NULL,
    output_tokens INTEGER      NOT NULL,
    -- KST 기준 날짜. "하루"의 경계가 어디인지에 따라 일일 상한의 값이 달라지므로 기준을 고정한다.
    usage_date    DATE         NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);
CREATE INDEX idx_ai_usage_skeleton ON ai_usage_records (skeleton_id);
CREATE INDEX idx_ai_usage_date ON ai_usage_records (usage_date);

-- 예산 설정 -----------------------------------------------------------------
-- 단일 행이다. CHECK 로 id 를 1 로 고정해 두 번째 행이 생길 수 없게 한다 — 설정 행이 둘이면
-- 어느 것이 유효한지 알 수 없고, 그 상태는 조회 순서에 따라 다르게 동작한다.
--
-- NULL 은 무제한이다. 기본값을 무제한으로 두지 않는 이유는 안전장치가 기본으로 꺼져 있으면
-- 안 되기 때문이다. 아래 값은 **실측 전 자리표시자**다:
--   챕터 20개 뼈대 ≈ 그래프 1회(~10k) + 개요 1회(~20k) + 본문 20회(~15k) ≈ 350k 토큰.
--   재시도와 여유를 더해 뼈대당 1M, 일일 20M 으로 둔다. 주제 하나를 끝까지 돌려 실측한 뒤
--   이 값을 바꾼다.
CREATE TABLE ai_budget_settings (
    id                       SMALLINT  PRIMARY KEY,
    per_skeleton_token_limit BIGINT,
    daily_token_limit        BIGINT,
    updated_by               VARCHAR(200),
    created_at               TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP NOT NULL,
    CONSTRAINT ck_ai_budget_single_row CHECK (id = 1)
);

INSERT INTO ai_budget_settings (id, per_skeleton_token_limit, daily_token_limit, created_at, updated_at)
VALUES (1, 1000000, 20000000, NOW(), NOW());
