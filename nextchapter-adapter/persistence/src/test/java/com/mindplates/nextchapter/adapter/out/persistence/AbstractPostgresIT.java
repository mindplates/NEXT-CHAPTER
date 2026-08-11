package com.mindplates.nextchapter.adapter.out.persistence;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL 에 붙는 어댑터 테스트의 공통 기반.
 *
 * <p>이 제품의 위험은 도메인 로직이 아니라 저장소 경계에 몰려 있다 — 유일 제약, 벡터 검색, outbox,
 * 버전 키. 그 구간을 목으로 덮으면 정작 깨질 곳이 테스트되지 않는다. 그래서 어댑터는 목이 아니라
 * 컨테이너에 붙인다.
 *
 * <p>이미지가 {@code pgvector/pgvector} 인 이유는 마이그레이션 V1 이 {@code CREATE EXTENSION
 * vector} 로 시작하기 때문이다. 순수 postgres 이미지로는 첫 마이그레이션부터 실패한다.
 *
 * <p>컨테이너를 이 클래스에 static 으로 둬 하위 테스트 클래스들이 하나를 공유한다. 클래스마다
 * 띄우면 테스트 시간이 컨테이너 기동 시간 × 클래스 수가 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public abstract class AbstractPostgresIT {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PGVECTOR_IMAGE);
}
