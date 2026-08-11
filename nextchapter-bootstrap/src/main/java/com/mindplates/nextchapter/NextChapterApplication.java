package com.mindplates.nextchapter;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
// outbox 퍼블리셔가 주기적으로 돌아야 한다 — 커밋 시점을 알 수 없으므로 폴링이다.
@EnableScheduling
public class NextChapterApplication {

    public static void main(String[] args) {
        // 모든 날짜/시간 저장·비교를 UTC 로 통일한다. 신호 레코드의 at, 챕터 버전, 잡 타임스탬프가
        // 서로 다른 시간대로 기록되면 수정 전후 비교(3개월 성공 기준 2번)가 어긋난다.
        // 반드시 SpringApplication.run 이전 — 이후 컴포넌트가 시각을 읽기 전에 적용해야 한다.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
        SpringApplication.run(NextChapterApplication.class, args);
    }
}
