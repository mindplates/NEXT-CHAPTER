-- ---------------------------------------------------------------------------
-- V17 — 집단 루프 임계치 설정 (M5 P5.2)
--
-- ai_budget_settings 와 같은 모양이다 — 단일 행, CHECK 로 id 를 1 로 고정해 두 번째 행이 생길 수
-- 없게 한다. 설정 행이 둘이면 어느 것이 유효한지 알 수 없고, 조회 순서에 따라 다르게 동작한다.
--
-- 기본값은 CLAUDE.md 가 예시로 든 낮은 절대치를 그대로 쓴다 — 같은 지점 질문 5건, 또는 시도 20회
-- 이상에서 오답률 40% 초과. 비율이 아니라 절대치인 이유는 초기 표본이 작아서다: 소비자 3명 중
-- 1명만 물어도 33% 가 되어 오작동한다.
-- ---------------------------------------------------------------------------
CREATE TABLE collective_threshold_settings (
    id                  SMALLINT  PRIMARY KEY,
    question_threshold  INTEGER   NOT NULL,
    min_attempts        INTEGER   NOT NULL,
    wrong_rate_percent  INTEGER   NOT NULL,
    updated_by          VARCHAR(200),
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT ck_collective_threshold_single_row CHECK (id = 1)
);

INSERT INTO collective_threshold_settings
       (id, question_threshold, min_attempts, wrong_rate_percent, created_at, updated_at)
VALUES (1, 5, 20, 40, NOW(), NOW());
