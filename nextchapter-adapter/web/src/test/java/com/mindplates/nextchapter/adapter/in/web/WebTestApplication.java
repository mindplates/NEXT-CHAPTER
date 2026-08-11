package com.mindplates.nextchapter.adapter.in.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 테스트 전용 부트 진입점. 조립은 bootstrap 이 하지만 Boot 의 웹 슬라이스가
 * {@code @SpringBootConfiguration} 을 필요로 하므로 테스트 소스에만 둔다.
 */
@SpringBootApplication
public class WebTestApplication {}
