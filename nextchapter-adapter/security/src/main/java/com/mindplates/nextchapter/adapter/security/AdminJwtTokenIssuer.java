package com.mindplates.nextchapter.adapter.security;

import com.mindplates.nextchapter.application.admin.port.out.AdminTokenIssuerPort;
import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** 관리자 JWT 발급. 클레임 구성이 {@link SecurityConfig} 의 권한 변환 규칙과 짝을 이룬다. */
@Component
public class AdminJwtTokenIssuer implements AdminTokenIssuerPort {

    /** 권한 클레임 이름. 디코더 쪽 변환 설정과 반드시 같아야 한다. */
    static final String ROLES_CLAIM = "roles";

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public AdminJwtTokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public AdminTokenView issue(AdminAccount account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.email())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLES_CLAIM, List.of(account.role().name()))
                .claim("adminId", account.id())
                .build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new AdminTokenView(token, TOKEN_TYPE, expiresAt);
    }
}
