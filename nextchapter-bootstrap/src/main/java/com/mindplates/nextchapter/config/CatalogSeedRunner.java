package com.mindplates.nextchapter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.catalog.port.in.SeedCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.SeedCatalogCommand;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 초기 카탈로그를 기동 시점에 투입한다.
 *
 * <p>bootstrap 모듈에 두는 이유는 {@code AdminBootstrapRunner} 와 같다 — 유스케이스는 "없으면 만든다"만
 * 알고, "언제 부르는가"는 기동 절차의 문제다.
 *
 * <p>시드 파일 경로를 설정으로 뺐다. 운영에서는 다른 카탈로그를 쓰게 되고(M7 의 전량 30~50개), 그때 코드를
 * 고쳐야 한다면 그 파일이 배포 산출물에 갇힌다.
 *
 * <p><b>실패해도 기동을 막지 않는다.</b> 카탈로그는 콘텐츠이고 애플리케이션은 카탈로그가 비어도 뜬다 —
 * 시드 파일 오타로 서비스가 올라오지 않는 쪽이 더 나쁘다. 대신 경고를 남긴다.
 */
@Component
public class CatalogSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeedRunner.class);

    private final SeedCatalogUseCase seedCatalogUseCase;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String location;

    public CatalogSeedRunner(
            SeedCatalogUseCase seedCatalogUseCase,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            @Value("${nextchapter.catalog.seed.enabled:true}") boolean enabled,
            @Value("${nextchapter.catalog.seed.location:classpath:catalog/seed.json}") String location) {
        this.seedCatalogUseCase = seedCatalogUseCase;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.location = location;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[카탈로그 시드] 비활성화됨 (nextchapter.catalog.seed.enabled=false)");
            return;
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("[카탈로그 시드] 시드 파일이 없어 건너뛴다: {}", location);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            seedCatalogUseCase.seed(objectMapper.readValue(in, SeedCatalogCommand.class));
        } catch (Exception e) {
            // 기동을 막지 않는다 — 카탈로그가 비어도 애플리케이션은 뜨고, 운영자가 관리 화면에서 채울 수 있다.
            log.error("[카탈로그 시드] 투입 실패 ({}): {}", location, e.getMessage());
        }
    }
}
