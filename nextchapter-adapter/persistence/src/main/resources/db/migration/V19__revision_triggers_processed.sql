-- ---------------------------------------------------------------------------
-- V19 — 수정 트리거 처리 표시 (M5 P5.3)
--
-- 수정안 생성은 폴링이다(outbox·신호 발행과 같은 이유 — 커밋 시점을 컨슈머가 알 수 없다).
-- NULL 인 행만 다음 폴링이 다시 집어 간다. AI 호출이 느리고 비용이 들기 때문에 FOR UPDATE
-- SKIP LOCKED 로 잠가야 인스턴스 두 개가 같은 트리거로 제안을 두 번 만들지 않는다.
-- ---------------------------------------------------------------------------
ALTER TABLE revision_triggers ADD COLUMN processed_at TIMESTAMP;

CREATE INDEX idx_revision_triggers_unprocessed ON revision_triggers (id) WHERE processed_at IS NULL;
