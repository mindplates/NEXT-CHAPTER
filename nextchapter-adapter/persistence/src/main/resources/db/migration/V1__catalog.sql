-- ---------------------------------------------------------------------------
-- V1 — 주제 카탈로그 (도메인 > 분야 > 주제) + 주제에 1:1 로 붙는 뼈대
--
-- 스키마의 원천은 이 마이그레이션이다. ddl-auto=none 이므로 JPA 엔티티가 테이블을
-- 만들지 않는다. 엔티티가 스키마를 만들게 두면 "실행해 봐야 아는 스키마"가 되고,
-- 컬럼 타입·인덱스·제약이 코드 리뷰에 보이지 않는다.
--
-- pgvector 확장은 로컬 compose 의 init 스크립트가 깔지만 Testcontainers 는 그
-- 스크립트를 태우지 않는다. 그래서 여기서 한 번 더 보장한다 — 주제 임베딩(P1.4)이
-- 이 확장에 의존한다.
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS vector;

-- L1 도메인 --------------------------------------------------------------------
CREATE TABLE catalog_domains (
    id          BIGSERIAL    PRIMARY KEY,
    slug        VARCHAR(80)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_catalog_domains_slug UNIQUE (slug)
);

-- L2 분야 ---------------------------------------------------------------------
-- slug 는 도메인 안에서만 유일하다. 서로 다른 도메인이 같은 이름의 분야를 갖는 것은
-- 자연스럽고(예: 수학 > 통계, 컴퓨터과학 > 통계), 분야에는 뼈대가 붙지 않으므로
-- 갈라져도 신호가 분산되지 않는다.
CREATE TABLE catalog_fields (
    id          BIGSERIAL    PRIMARY KEY,
    domain_id   BIGINT       NOT NULL REFERENCES catalog_domains (id),
    slug        VARCHAR(80)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_catalog_fields_slug UNIQUE (domain_id, slug)
);
CREATE INDEX idx_catalog_fields_domain ON catalog_fields (domain_id);

-- L3 주제 ---------------------------------------------------------------------
-- 여기서는 slug 를 전역 유일로 둔다. 주제가 뼈대의 부착점이므로, 같은 slug 가 두 벌
-- 생기는 것이 곧 신호 분산이다 — 주제 통합이 막으려던 바로 그 상태다.
CREATE TABLE catalog_topics (
    id          BIGSERIAL     PRIMARY KEY,
    field_id    BIGINT        NOT NULL REFERENCES catalog_fields (id),
    slug        VARCHAR(120)  NOT NULL,
    name        VARCHAR(160)  NOT NULL,
    description TEXT,
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    CONSTRAINT uq_catalog_topics_slug UNIQUE (slug)
);
CREATE INDEX idx_catalog_topics_field ON catalog_topics (field_id);

-- 주제 별칭 -------------------------------------------------------------------
-- "기계학습", "ML 입문" 을 같은 주제로 수렴시키는 가장 싼 수단이다. LLM 매핑(P1.5)
-- 앞단에 두면 이미 판정이 끝난 입력에 대해 모델을 부르지 않는다 — 비용도 지연도 0 이고
-- 결과가 결정적이다.
--
-- normalized 가 전역 유일인 이유: 같은 표현이 두 주제를 가리키면 그 입력은 어느 뼈대로
-- 갈지 알 수 없다. 그 모호함은 등록 시점에 막아야 하고, 런타임에 발견하면 이미
-- 신호가 갈라진 뒤다.
CREATE TABLE catalog_topic_aliases (
    id         BIGSERIAL    PRIMARY KEY,
    topic_id   BIGINT       NOT NULL REFERENCES catalog_topics (id) ON DELETE CASCADE,
    alias      VARCHAR(200) NOT NULL,
    normalized VARCHAR(200) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_topic_aliases_normalized UNIQUE (normalized)
);
CREATE INDEX idx_topic_aliases_topic ON catalog_topic_aliases (topic_id);

-- 뼈대 -----------------------------------------------------------------------
-- topic_id 의 UNIQUE 가 "뼈대는 최말단 주제에 1:1" 제약 그 자체다. 애플리케이션
-- 검사만 두면 동시 요청 두 건이 같은 주제로 뼈대를 두 개 만들 수 있다 — 그 순간
-- 신호가 반으로 갈린다.
CREATE TABLE skeletons (
    id         BIGSERIAL   PRIMARY KEY,
    topic_id   BIGINT      NOT NULL REFERENCES catalog_topics (id),
    status     VARCHAR(40) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_skeletons_topic UNIQUE (topic_id)
);
