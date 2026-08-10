-- pgvector — 주제 임베딩을 주제 메타와 같은 트랜잭션에 묶기 위해 Postgres 쪽에 둔다.
-- (챕터 임베딩은 Neo4j 네이티브 벡터 인덱스. 같은 벡터를 두 곳에 중복 저장하지 않는다.)
CREATE EXTENSION IF NOT EXISTS vector;
