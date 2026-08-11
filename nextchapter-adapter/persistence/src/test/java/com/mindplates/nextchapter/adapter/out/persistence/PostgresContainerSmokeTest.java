package com.mindplates.nextchapter.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * M0 의 Testcontainers 검증용 스모크 테스트.
 *
 * <p>이 제품의 위험은 도메인 로직이 아니라 저장소 경계에 몰려 있어서 어댑터는 실제 저장소에
 * 붙여 검증한다. 그 방식이 CI(Docker-in-Docker)에서 동작하는지를 골격 단계에서 한 번 확인해
 * 둔다 — M2 이후에 발견하면 전 어댑터 테스트가 한꺼번에 막힌다.
 *
 * <p>pgvector 확장까지 확인하는 이유는 주제 임베딩이 이 확장에 의존하기 때문이다. 이미지에
 * 확장이 없으면 M1 에서야 알게 된다.
 */
@Testcontainers
@DisplayName("PostgreSQL 컨테이너 스모크 테스트")
class PostgresContainerSmokeTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PGVECTOR_IMAGE);

    @Test
    @DisplayName("pgvector 확장을 설치할 수 있다")
    void createsVectorExtension() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {

            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");

            try (ResultSet result =
                    statement.executeQuery("SELECT extname FROM pg_extension WHERE extname = 'vector'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("extname")).isEqualTo("vector");
            }
        }
    }
}
