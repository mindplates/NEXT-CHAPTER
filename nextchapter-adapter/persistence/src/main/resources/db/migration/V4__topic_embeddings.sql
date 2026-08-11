-- ---------------------------------------------------------------------------
-- V4 — 주제 임베딩 (pgvector)
--
-- 주제 메타와 같은 트랜잭션에 묶이므로 Postgres 에 둔다. 챕터 임베딩은 Neo4j 의 네이티브
-- 벡터 인덱스에 있고, 같은 벡터를 두 곳에 중복 저장하지 않는다.
--
-- 별도 테이블인 이유:
--   1) 모델 추적 — 어느 모델로 만든 벡터인지가 재임베딩 완료 판정의 기준이다
--   2) 트리 조회 비용 — 관리 화면 트리는 주제를 전부 읽는다. 1024차원 벡터가 주제 행에
--      붙어 있으면 벡터가 필요 없는 조회마다 그 무게를 함께 읽는다
--
-- `vector` 에 차원을 지정하지 않는다. 임베딩 모델을 관리 화면에서 교체할 수 있게 만든
-- 이상 차원은 고정값이 아니다. 차원을 박으면 모델 교체가 마이그레이션을 요구하고, 그건
-- "관리 화면에서 교체한다"는 결정과 정면으로 부딪힌다.
--
-- 대가는 벡터 인덱스(HNSW·IVFFlat)를 만들 수 없다는 점이다 — pgvector 인덱스는 차원이
-- 확정된 컬럼만 받는다. 감수하는 이유는 주제 수가 카탈로그 크기에 묶여 있어서다(초기
-- 30~50개, 성장해도 수천). 그 규모의 순차 스캔은 문제가 되지 않는다. 반대로 챕터
-- 임베딩은 뼈대마다 수십 개씩 늘어나므로 인덱스가 필수고, 그래서 Neo4j 쪽에 있다.
-- ---------------------------------------------------------------------------
CREATE TABLE catalog_topic_embeddings (
    topic_id    BIGINT       PRIMARY KEY REFERENCES catalog_topics (id) ON DELETE CASCADE,
    model       VARCHAR(120) NOT NULL,
    dimension   INTEGER      NOT NULL,
    -- 무엇을 임베딩했는지. 재임베딩이 같은 텍스트로 다시 계산할 수 있어야 하고,
    -- 매칭이 빗나갔을 때 "무엇을 비교했는가"를 볼 수 있어야 한다.
    source_text TEXT         NOT NULL,
    embedding   vector       NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- 재임베딩 대상("현재 모델이 아닌 행")을 찾는 조회가 이 인덱스를 쓴다.
CREATE INDEX idx_topic_embeddings_model ON catalog_topic_embeddings (model);
