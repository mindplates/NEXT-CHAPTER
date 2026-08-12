package com.mindplates.nextchapter.adapter.security;

import com.mindplates.nextchapter.application.user.port.out.UserTokenIssuerPort;
import com.mindplates.nextchapter.application.user.view.UserTokenView;
import com.mindplates.nextchapter.application.user.view.UserView;
import com.mindplates.nextchapter.domain.user.model.User;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 사용자 JWT 발급.
 *
 * <p>subject 가 <b>사용자 ID</b>다 — 관리자 토큰이 이메일을 subject 로 쓰는 것과 다르다. 제공자가
 * 이메일을 주지 않을 수 있어(Kakao 는 동의 항목) 이메일을 정체성으로 쓸 수 없고, 무엇보다 사용자
 * 레이어와 신호가 ID 로 붙는다. subject 를 이메일로 두면 요청마다 이메일→ID 조회가 한 번 더 붙는다.
 *
 * <p>역할 클레임을 {@code USER} 로 둬 관리자 토큰과 구분한다. 같은 키로 서명하므로 <b>역할이 유일한
 * 구분선</b>이고, 그래서 두 필터 체인이 각자 역할을 확인한다 — 확인하지 않으면 사용자 토큰으로 승인
 * 대기열에 접근할 수 있다.
 */
@Component
public class UserJwtTokenIssuer implements UserTokenIssuerPort {

    /** 사용자 역할. 관리자의 {@code ADMIN} 과 같은 클레임에 들어간다. */
    public static final String ROLE_USER = "USER";

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public UserJwtTokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public UserTokenView issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.userTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AdminJwtTokenIssuer.ROLES_CLAIM, List.of(ROLE_USER))
                .claim("provider", user.provider().name())
                .build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new UserTokenView(token, TOKEN_TYPE, expiresAt, UserView.from(user));
    }
}
