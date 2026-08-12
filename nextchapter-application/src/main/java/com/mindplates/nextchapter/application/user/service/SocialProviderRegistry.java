package com.mindplates.nextchapter.application.user.service;

import com.mindplates.nextchapter.application.user.port.in.GetSupportedSocialProvidersUseCase;
import com.mindplates.nextchapter.application.user.port.out.SocialProfileClientPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final Map<SocialProvider, SocialProfileClientPort> clients = new EnumMap<>(SocialProvider.class);

    public SocialProviderRegistry(List<SocialProfileClientPort> ports) {
        ports.forEach(port -> clients.put(port.provider(), port));
    }

    @Override
    public Set<SocialProvider> supported() {
        return Set.copyOf(clients.keySet());
    }

    public SocialProfileClientPort require(SocialProvider provider) {
        SocialProfileClientPort client = clients.get(provider);
        if (client == null) {
            throw new InvalidOperationException("지원하지 않는 소셜 제공자입니다: " + provider);
        }
        return client;
    }
}
