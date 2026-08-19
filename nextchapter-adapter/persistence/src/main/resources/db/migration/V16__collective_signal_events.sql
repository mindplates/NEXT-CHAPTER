-- ---------------------------------------------------------------------------
-- V16 — 집단 신호 집계 원장 (M5 P5.1)
--
-- Kafka 컨슈머가 신호를 이 테이블로 멱등하게 복사한다. PK 가 signal_id 인 이유가 멱등성의 전부다 —
-- Kafka 는 at-least-once 이므로 같은 신호가 두 번 올 수 있고, 카운터를 직접 올리면 그 중복이 집계를
-- 두 번 센다. 대신 원장에 한 번만 남기고, 집계는 GROUP BY(FILTER) 로 그때그때 구한다 — 따로 올리고
-- 내리는 카운터가 없으니 어긋날 수도 없다.
--
-- 보충 블록과 PROGRESS(블록 없음)는 담지 않는다 — 컨슈머가 들어오는 시점에 거른다. 보충 블록은 그
-- 사용자에게만 있어 여럿이 겹쳐 볼 수 없고, PROGRESS 는 애초에 블록 단위 집계 대상이 아니다.
-- ---------------------------------------------------------------------------
CREATE TABLE collective_signal_events (
    -- 원본 신호(signals.id)를 그대로 PK 로 쓴다 — 재수신을 저장 시점에 막는 멱등 키다.
    signal_id       BIGINT      PRIMARY KEY,
    chapter_id      BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    chapter_version INTEGER     NOT NULL,
    block_id        VARCHAR(20) NOT NULL,
    -- 딜리버리 형태. 집계 키에는 넣지 않는다 — 형태별로 나누면 초기 규모에서 어느 쪽도 임계치를 못
    -- 넘는다. 수정안의 근거로 형태별 분해를 만들 때(P5.3)만 읽는다.
    format          VARCHAR(20) NOT NULL,
    type            VARCHAR(40) NOT NULL,
    -- QUIZ_ANSWER 에만 있다. 다른 종류는 NULL.
    correct         BOOLEAN,
    occurred_at     TIMESTAMP   NOT NULL,
    consumed_at     TIMESTAMP   NOT NULL
);

-- 집계 축: 같은 챕터·같은 버전·같은 블록에 무엇이 몇 건 모였는가. P5.2 임계치 검사가 이 인덱스로 읽는다.
CREATE INDEX idx_collective_signal_events_block
    ON collective_signal_events (chapter_id, chapter_version, block_id, type);
