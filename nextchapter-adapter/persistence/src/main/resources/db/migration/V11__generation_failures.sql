-- ---------------------------------------------------------------------------
-- V11 — 생성 실패 운영자 큐
--
-- 재시도를 소진한 항목이 여기로 온다. 큐가 필요한 이유는 실패가 **조용히 멈추는**
-- 종류이기 때문이다 — 컨슈머가 예외를 올리고 재시도가 끝나면 메시지는 사라지고, 뼈대는
-- 생성 중 상태로 남고, 아무도 그 사실을 모른다.
--
-- 메시지 좌표(topic/partition/offset)를 저장하는 이유는 **재시도가 원본 페이로드를
-- 필요로 하기 때문이다.** 뼈대 ID 만 남기면 운영자가 할 수 있는 것은 "처음부터 다시"뿐이고,
-- 그건 이미 성공한 챕터의 생성비를 다시 쓰는 일이다.
-- ---------------------------------------------------------------------------
CREATE TABLE generation_failures (
    id               BIGSERIAL    PRIMARY KEY,
    -- NULL 은 페이로드를 읽지 못한 경우다. 그때도 큐에는 올려야 한다 — 읽을 수 없는 메시지가
    -- 조용히 사라지는 것이 이 큐를 만든 이유 그 자체다. 뼈대를 모르므로 뼈대 실패 표시는 못 한다.
    skeleton_id      BIGINT,
    -- 본문 단계만 채워진다. 그래프·개요 실패는 뼈대 단위다.
    chapter_id       BIGINT,
    stage            VARCHAR(40)  NOT NULL,
    topic            VARCHAR(200) NOT NULL,
    partition_number INTEGER      NOT NULL,
    record_offset    BIGINT       NOT NULL,
    -- 원본 페이로드. 이것이 있어야 운영자가 그 항목만 다시 돌릴 수 있다.
    payload          TEXT         NOT NULL,
    error_type       VARCHAR(200) NOT NULL,
    error_message    TEXT,
    attempts         INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    resolved_by      VARCHAR(200),
    resolved_at      TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

-- 큐 화면의 기본 조회다. status 를 앞에 두면 열린 항목만 훑는다.
CREATE INDEX idx_generation_failures_status ON generation_failures (status, created_at);
CREATE INDEX idx_generation_failures_skeleton ON generation_failures (skeleton_id);

-- 같은 메시지가 두 번 소진될 수 있다 — 파티션 재할당, 오프셋 되돌림, 컨슈머 재기동.
-- 그때 큐에 같은 항목이 여러 줄 쌓이면 운영자가 몇 개가 실제 문제인지 알 수 없다.
-- 애플리케이션 검사만으로는 컨슈머 인스턴스가 둘일 때 조용히 통과한다.
CREATE UNIQUE INDEX uq_generation_failures_record ON generation_failures (topic, partition_number, record_offset);
