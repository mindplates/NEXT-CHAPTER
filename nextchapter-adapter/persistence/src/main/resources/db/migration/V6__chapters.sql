-- ---------------------------------------------------------------------------
-- V6 — 챕터와 챕터 버전
--
-- 챕터 본문의 원천은 Postgres 다. Neo4j 는 ID·관계·임베딩만 갖는 얇은 그래프이고,
-- 수정 이력과 승인 기록이 본문과 같은 트랜잭션에 묶여야 하기 때문이다.
-- ---------------------------------------------------------------------------
CREATE TABLE chapters (
    id                BIGSERIAL    PRIMARY KEY,
    skeleton_id       BIGINT       NOT NULL REFERENCES skeletons (id) ON DELETE CASCADE,
    -- 뼈대 안에서 유일한 사람이 읽을 수 있는 키. 그래프 생성이 챕터 간 관계를 ID 가 아직
    -- 없는 시점에 표현해야 하므로 필요하다 (LLM 은 "gradient-descent → loss-function"
    -- 처럼 키로 관계를 말한다).
    chapter_key       VARCHAR(120) NOT NULL,
    title             VARCHAR(300) NOT NULL,
    summary           TEXT,
    -- 0 은 "본문이 아직 없음"이다. 생성 파이프라인이 ③ 본문 생성에 도달하기 전의 상태고,
    -- 별도 플래그를 두지 않는 이유는 버전 번호 자체가 그 사실을 이미 표현하기 때문이다.
    current_version   INTEGER      NOT NULL DEFAULT 0,
    -- 블록 ID 발급용 단조 증가 카운터. 절대 되돌리거나 재사용하지 않는다.
    --
    -- 이 컬럼이 블록 ID 규칙의 핵심이다. 재사용을 허용하면 v1 에서 문단이던 b3 가 v5 에서
    -- 다른 내용이 될 수 있고, 그 순간 두 버전의 신호가 조용히 한 블록에 섞인다 — 수정 전후
    -- 오답률 비교(3개월 성공 기준 2번)가 근거를 잃는다.
    block_id_sequence INTEGER      NOT NULL DEFAULT 0,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_chapters_key UNIQUE (skeleton_id, chapter_key)
);
CREATE INDEX idx_chapters_skeleton ON chapters (skeleton_id);

-- 챕터 버전 -----------------------------------------------------------------
-- 수정될 때마다 본문 전문을 스냅샷으로 쌓는다. 델타 저장이 아닌 이유는 세 가지다.
--   1) 승인 이력 요구사항("언제 무엇이 왜 바뀌었나")이 diff 재구성이 아니라 단순 조회로 끝나야 한다
--   2) 수정 전후 오답률 비교가 임의 시점의 본문을 필요로 한다
--   3) 챕터 본문은 수 KB 라 스냅샷 저장 비용이 사실상 무시할 만하다
--
-- body 가 jsonb 인 이유는 블록 문서가 딜리버리 중립 표현이기 때문이다. 블록을 관계형으로
-- 펼치면 렌더러가 필요로 하는 "순서 있는 이형 블록 목록"을 매번 재조립해야 하고, 블록 타입이
-- 늘 때마다 스키마가 바뀐다.
CREATE TABLE chapter_versions (
    id             BIGSERIAL   PRIMARY KEY,
    chapter_id     BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    version        INTEGER     NOT NULL,
    body           JSONB       NOT NULL,
    -- "이 챕터는 개선되었습니다" 배지가 노출할 변경 요약.
    change_summary TEXT,
    source         VARCHAR(40) NOT NULL,
    created_by     VARCHAR(200),
    created_at     TIMESTAMP   NOT NULL,
    CONSTRAINT uq_chapter_versions UNIQUE (chapter_id, version)
);
CREATE INDEX idx_chapter_versions_chapter ON chapter_versions (chapter_id, version DESC);
