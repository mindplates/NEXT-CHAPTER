-- ---------------------------------------------------------------------------
-- V12 — 배치 잡
--
-- 뼈대 생성은 비동기 일괄이므로 Batch API 조건에 부합한다(전 토큰 50% 할인, 최대 24시간).
-- 비동기 생성 결정이 그대로 비용 절반으로 돌아온다.
--
-- **벤더와 모델을 함께 저장하는 것이 이 테이블의 핵심이다.** 배치는 최대 24시간 살아 있고
-- 그 사이 관리자가 단계 설정을 바꿀 수 있다. 조회할 때 현재 설정을 다시 읽으면 다른 벤더의
-- API 로 다른 벤더의 배치 ID 를 묻게 되고, 그건 "배치를 찾을 수 없음"으로 나타난다 —
-- 제출한 배치가 조용히 유실된다.
-- ---------------------------------------------------------------------------
CREATE TABLE ai_batch_jobs (
    id             BIGSERIAL    PRIMARY KEY,
    skeleton_id    BIGINT       NOT NULL,
    stage          VARCHAR(40)  NOT NULL,
    vendor         VARCHAR(40)  NOT NULL,
    model          VARCHAR(120) NOT NULL,
    batch_id       VARCHAR(200) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    item_count     INTEGER      NOT NULL,
    failure_reason TEXT,
    completed_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

-- 같은 배치를 두 번 반영하면 챕터마다 v2 가 쌓인다. 그건 수정이 아니라 중복이고, 이력에
-- "왜 바뀌었는지 설명할 수 없는 버전"을 남긴다.
CREATE UNIQUE INDEX uq_ai_batch_jobs_batch_id ON ai_batch_jobs (batch_id);

-- 폴러의 조회다. 끝나지 않은 것만 훑는다.
CREATE INDEX idx_ai_batch_jobs_status ON ai_batch_jobs (status, created_at);
CREATE INDEX idx_ai_batch_jobs_skeleton ON ai_batch_jobs (skeleton_id);
