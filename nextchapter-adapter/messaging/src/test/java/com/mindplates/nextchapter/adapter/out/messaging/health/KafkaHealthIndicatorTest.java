package com.mindplates.nextchapter.adapter.out.messaging.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * 붙을 수 없는 브로커를 가리켰을 때 <b>예외가 아니라 DOWN</b>이 나오는지를 본다.
 *
 * <p>헬스 인디케이터가 예외를 던지면 액추에이터 응답 전체가 500 이 되고, Kafka 하나 때문에 다른 저장소
 * 상태까지 볼 수 없게 된다. 장애 중에 가장 필요한 정보가 그때 사라진다.
 *
 * <p>실제 브로커에 붙는 UP 경로는 {@code GenerationEventKafkaPublisherIT} 가 컨테이너로 덮는다 —
 * 여기서 브로커를 또 띄우면 같은 사실을 두 번 확인하며 테스트 시간만 늘어난다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Kafka 헬스체크")
class KafkaHealthIndicatorTest {

    @Mock
    KafkaAdmin kafkaAdmin;

    @Test
    @DisplayName("붙을 수 없으면 예외가 아니라 DOWN 을 돌려준다")
    void unreachableBrokerReportsDown() {
        // 예약되지 않은 포트를 가리켜 연결이 반드시 실패하게 한다.
        when(kafkaAdmin.getConfigurationProperties())
                .thenReturn(Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1",
                        AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1000,
                        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 500));

        Health health = new KafkaHealthIndicator(kafkaAdmin).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    /** 스택 트레이스가 응답에 실리면 설정값·내부 경로가 함께 흘러나간다. */
    @Test
    @DisplayName("오류 상세는 예외 타입과 메시지까지만 노출한다")
    void errorDetailIsNotAStackTrace() {
        when(kafkaAdmin.getConfigurationProperties())
                .thenReturn(Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1",
                        AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1000,
                        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 500));

        Object error =
                new KafkaHealthIndicator(kafkaAdmin).health().getDetails().get("error");

        assertThat(error).isInstanceOf(String.class);
        assertThat((String) error).doesNotContain("\tat ");
    }
}
