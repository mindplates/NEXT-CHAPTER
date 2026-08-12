-- ---------------------------------------------------------------------------
-- V14 — 신호
--
-- 같은 신호를 두 방향으로 쓴다.
--   개인 루프 — 이 테이블에 동기 기록하고, 다음 챕터 결정 시 곧바로 읽는다
--   집단 루프 — Kafka 로 흘려 집계 컨슈머가 받는다 (M5)
--
-- 전부 Kafka 로만 흘리면 개인 루프가 최종 일관성이 되어 "방금 답한 결과가 다음 챕터에
-- 반영되지 않는" 타이밍 문제가 생긴다. 그래서 두 경로다.
--
-- **이 테이블이 곧 집단 루프의 outbox다.** 행 자체가 이벤트이므로 별도 outbox 테이블을 두지
-- 않고 published_at 을 붙였다. 커밋 뒤에 바로 발행하고 실패를 로그로만 남기면 그 신호는
-- 집계에 영원히 도달하지 않고, 그 유실은 "임계치가 트리거되지 않는다"로 조용히 나타난다.
-- ---------------------------------------------------------------------------
CREATE TABLE signals (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    chapter_id      BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    -- 사용자가 **실제로 소비한** 버전. 무조건 최신 버전으로 기록하면 구버전 영상을 본 사람의
    -- 오답이 최신 버전의 실패로 잘못 집계되고, 그러면 수정 전후 비교(3개월 성공 기준 2번)가
    -- 근거를 잃는다.
    chapter_version INTEGER     NOT NULL,
    -- 신호가 붙는 지점. 이것이 없으면 집단 루프의 정밀도가 챕터 단위로 떨어져 "이 챕터가
    -- 어렵다"까지만 알 수 있다. 소비 진행(PROGRESS)에만 NULL 이 허용된다 — 그건 본래
    -- 챕터 단위 사실이다.
    block_id        VARCHAR(20),
    -- 딜리버리 형태. 임계치 판정에는 쓰지 않고(형태별로 나누면 신호가 쪼개져 초기 규모에서
    -- 어느 쪽도 임계치를 못 넘는다) 수정안의 근거로 형태별 분해를 만들 때만 쓴다.
    format          VARCHAR(20) NOT NULL,
    type            VARCHAR(40) NOT NULL,
    -- 타입별 구조. QUIZ_ANSWER 의 correct, QUESTION·ERROR_REPORT 의 text 처럼 타입마다
    -- 다르므로 컬럼으로 펼치지 않는다.
    payload         JSONB       NOT NULL,
    occurred_at     TIMESTAMP   NOT NULL,
    -- NULL 이면 아직 집계 경로로 발행되지 않았다.
    published_at    TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL
);

-- 갱신되지 않는 테이블이므로 updated_at 이 없다. 신호는 일어난 사실이고, 고쳐 쓰면 그 시점의
-- 집계 근거가 사라진다.

-- 집단 루프의 집계 축: 같은 챕터·같은 버전·같은 블록에 몇 건 모였는가.
CREATE INDEX idx_signals_aggregation ON signals (chapter_id, chapter_version, block_id, type);

-- 개인 루프의 축: 이 사용자가 이 챕터에서 무엇을 했는가. 다음 챕터 결정이 이 조회를 쓴다.
CREATE INDEX idx_signals_user_chapter ON signals (user_id, chapter_id);

-- 퍼블리셔는 미발행 행만 본다. 부분 인덱스라 발행된 행이 쌓여도 인덱스는 작게 유지된다.
CREATE INDEX idx_signals_unpublished ON signals (id) WHERE published_at IS NULL;
