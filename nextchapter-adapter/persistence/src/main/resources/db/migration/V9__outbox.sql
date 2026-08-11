-- ---------------------------------------------------------------------------
-- V9 — Transactional Outbox
--
-- Postgres 와 Neo4j 에 걸친 쓰기에는 분산 트랜잭션이 없다. 한쪽만 성공하면 그래프에 없는
-- 챕터, 또는 본문 없는 노드가 생긴다. 순차 쓰기 + 보상 방식은 보상 자체가 실패할 수 있고
-- 그 사이 프로세스가 죽으면 불일치가 남는다.
--
-- 그래서 본문·버전과 outbox 행을 **같은 커밋**에 넣는다. 커밋이 성공했다면 이벤트는 반드시
-- 남아 있고, 퍼블리셔가 언제든 다시 집어 갈 수 있다. 최종 일관성이지만 유실은 없다.
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(40)  NOT NULL,
    aggregate_id    VARCHAR(120) NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    -- 파티션 키. 뼈대 단위로 순서를 보장하기 위해 뼈대 ID 를 쓴다 — 간선 이벤트가 노드
    -- 이벤트보다 먼저 도착하면 Neo4j 에 속성 없는 유령 노드가 생긴다.
    partition_key   VARCHAR(120) NOT NULL,
    -- 멱등 키 = 챕터 ID + 버전. 재시도·재발행이 안전한 근거다.
    idempotency_key VARCHAR(200) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    -- NULL 이면 아직 발행되지 않았다.
    published_at    TIMESTAMP
);

-- 퍼블리셔는 미발행 행만 본다. 부분 인덱스라 발행된 행이 쌓여도 인덱스는 작게 유지된다 —
-- 전체 인덱스를 걸면 테이블이 커지는 만큼 폴링이 느려진다.
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- published 전환 조건이 "이 뼈대의 미발행 이벤트가 0건"을 확인한다. 퍼블리셔가 밀린 상태로
-- 공개하면 그래프 탐색이 빈 노드를 본다.
CREATE INDEX idx_outbox_pending_by_key ON outbox_events (partition_key) WHERE published_at IS NULL;
