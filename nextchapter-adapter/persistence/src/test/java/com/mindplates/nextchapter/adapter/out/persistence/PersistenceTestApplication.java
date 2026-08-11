package com.mindplates.nextchapter.adapter.out.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 테스트 전용 부트 진입점.
 *
 * <p>persistence 모듈에는 애플리케이션 클래스가 없다(조립은 bootstrap 이 한다). 하지만 Boot 의
 * 테스트 슬라이스는 {@code @SpringBootConfiguration} 을 찾아야 컨텍스트를 만들 수 있으므로,
 * 테스트 소스에만 하나 둔다. 프로덕션 클래스패스에는 나가지 않는다.
 */
@SpringBootApplication
public class PersistenceTestApplication {}
