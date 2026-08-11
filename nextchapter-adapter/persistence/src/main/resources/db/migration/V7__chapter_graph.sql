-- ---------------------------------------------------------------------------
-- V7 — 챕터 간 관계 (그래프)
--
-- 챕터는 선형 목차가 아니라 서로 연관 관계를 갖는 그래프로 연결된다. 사용자는 그래프의
-- 임의 지점에서 진입해 소비하고, 개인 루프(M4)가 "다음에 볼 챕터"를 이 관계 위에서 고른다.
--
-- 관계의 원천도 Postgres 다. Neo4j 는 outbox 를 거쳐 같은 관계를 받는 파생 저장소이고,
-- 통째 재구축이 가능해야 한다 — 원천이 Neo4j 면 재구축할 대상이 없다.
--
-- skeleton_id 를 관계 행에 중복해 두는 이유는 그래프 전체 조회가 가장 흔한 접근이기
-- 때문이다. 없으면 챕터 두 번을 조인해야 하고, 그건 뼈대마다 매번 일어난다.
-- ---------------------------------------------------------------------------
CREATE TABLE chapter_relations (
    id              BIGSERIAL   PRIMARY KEY,
    skeleton_id     BIGINT      NOT NULL REFERENCES skeletons (id) ON DELETE CASCADE,
    from_chapter_id BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    to_chapter_id   BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    relation_type   VARCHAR(40) NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    -- 같은 방향·같은 타입의 관계를 두 번 두지 않는다. 중복 간선은 경로 결정의 가중치를
    -- 왜곡하면서도 에러를 내지 않는다.
    CONSTRAINT uq_chapter_relations UNIQUE (from_chapter_id, to_chapter_id, relation_type),
    -- 자기 자신에 대한 관계는 어떤 타입으로도 의미가 없다. 선행 관계라면 시작 자체가 불가능하다.
    CONSTRAINT ck_chapter_relations_not_self CHECK (from_chapter_id <> to_chapter_id)
);
CREATE INDEX idx_chapter_relations_skeleton ON chapter_relations (skeleton_id);
CREATE INDEX idx_chapter_relations_from ON chapter_relations (from_chapter_id);
CREATE INDEX idx_chapter_relations_to ON chapter_relations (to_chapter_id);
