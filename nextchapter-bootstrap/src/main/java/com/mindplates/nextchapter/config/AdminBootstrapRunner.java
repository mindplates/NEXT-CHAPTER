package com.mindplates.nextchapter.config;

import com.mindplates.nextchapter.application.admin.port.in.EnsureBootstrapAdminUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 최초 관리자 계정을 만든다. 관리자 계정은 관리 화면에서 만들고 관리 화면은 관리자 인증을 통과해야
 * 열리므로, 어딘가에서 한 번은 순환을 끊어야 한다. 그 지점을 여기로 좁혔다.
 *
 * <p>bootstrap 모듈에 두는 이유는 이것이 조립 시점의 관심사이기 때문이다 — 유스케이스는 "계정이
 * 없으면 만든다"만 알고, "언제 부르는가"는 애플리케이션 기동 절차의 문제다.
 */
@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final EnsureBootstrapAdminUseCase ensureBootstrapAdminUseCase;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapRunner(
            EnsureBootstrapAdminUseCase ensureBootstrapAdminUseCase, AdminBootstrapProperties properties) {
        this.ensureBootstrapAdminUseCase = ensureBootstrapAdminUseCase;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.warn("[관리자] nextchapter.admin.bootstrap 설정이 없어 최초 관리자 계정을 만들지 않는다. "
                    + "ADMIN_BOOTSTRAP_EMAIL · ADMIN_BOOTSTRAP_PASSWORD 를 설정하면 계정이 없을 때 한 번 생성된다.");
            return;
        }
        boolean created = ensureBootstrapAdminUseCase.ensureBootstrapAdmin(
                properties.email(), properties.name(), properties.password());
        if (!created) {
            log.info("[관리자] 이미 계정이 있어 최초 계정 생성을 건너뛴다.");
        }
    }
}
