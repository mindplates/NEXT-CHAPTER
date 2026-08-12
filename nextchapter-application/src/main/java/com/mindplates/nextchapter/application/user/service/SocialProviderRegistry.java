package com.mindplates.nextchapter.application.user.service;

import com.mindplates.nextchapter.application.user.port.in.GetSupportedSocialProvidersUseCase;
import com.mindplates.nextchapter.application.user.port.out.SocialProfileClientPort;
import com.mindplates.nextchapter.application.user.view.SocialProviderView;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 제공자 어댑터를 제공자 값으로 찾아 준다. {@code AiVendorRegistry} 와 같은 형태이고 같은 이유다 —
 * <b>구현 존재 여부가 곧 지원 여부 조회</b>이므로 목록을 하드코딩하지 않는다.
 *
 * <p>없는 제공자에 대해 폴백하지 않는다. 기본 제공자로 조용히 넘어가면 사용자가 Kakao 버튼을 눌렀는데
 * Google 로 로그인되는 상태가 되고, 그건 계정이 갈라지는 것과 같다.
 */
@Service
public class SocialProviderRegistry implements GetSupportedSocialProvidersUseCase {

    private static final Logger log = LoggerFactory.getLogger(SocialProviderRegistry.class);

    /** {@code state} 안에서 임의 값과 제공자 이름을 나누는 문자. 클라이언트가 같은 규칙으로 읽는다. */
    public static final String STATE_SEPARATOR = ".";

    private final Map<SocialProvider, SocialProfileClientPort> clients = new EnumMap<>(SocialProvider.class);

    public SocialProviderRegistry(List<SocialProfileClientPort> ports) {
        ports.forEach(port -> clients.put(port.provider(), port));
    }

    /**
     * 동의 화면 주소를 만들 수 없는 제공자는 목록에서 <b>빠진다.</b> 설정이 불완전하면 어댑터가 거절하는데,
     * 그 제공자의 버튼을 그려 주면 사용자가 누른 뒤에야 실패를 본다.
     */
    @Override
    public List<SocialProviderView> available(String redirectUri, String state) {
        return clients.values().stream()
                .sorted(java.util.Comparator.comparing(SocialProfileClientPort::provider))
                .flatMap(client -> uriOf(client, redirectUri, state).stream())
                .toList();
    }

    private Optional<SocialProviderView> uriOf(SocialProfileClientPort client, String redirectUri, String state) {
        try {
            return Optional.of(new SocialProviderView(
                    client.provider(), client.authorizationUri(redirectUri, stamp(state, client))));
        } catch (InvalidOperationException e) {
            log.debug("[소셜 로그인] {} 는 설정되지 않아 목록에서 제외한다", client.provider());
            return Optional.empty();
        }
    }

    /**
     * {@code state} 에 제공자 이름을 함께 담는다.
     *
     * <p>제공자는 콜백에 <b>자기 이름을 돌려주지 않는다.</b> 콜백 주소를 제공자마다 다르게 두는 방법도
     * 있지만, 그러면 제공자를 추가할 때마다 제공자 콘솔에 주소를 하나 더 등록해야 한다. {@code state} 는
     * 어차피 왕복하는 값이므로 여기에 실어 보내는 편이 싸다.
     *
     * <p>이 값은 위조 방지용이 아니다 — 위조 방지는 클라이언트가 만든 임의 값을 대조하는 쪽이 한다.
     */
    private static String stamp(String state, SocialProfileClientPort client) {
        return (state == null || state.isBlank() ? "" : state)
                + STATE_SEPARATOR
                + client.provider().name();
    }

    public SocialProfileClientPort require(SocialProvider provider) {
        SocialProfileClientPort client = clients.get(provider);
        if (client == null) {
            throw new InvalidOperationException("지원하지 않는 소셜 제공자입니다: " + provider);
        }
        return client;
    }
}
