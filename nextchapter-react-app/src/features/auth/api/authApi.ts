import { getJson, postJson } from '../../../shared/api/httpClient'

export type SocialProvider = 'GOOGLE' | 'KAKAO'

export interface UserProfile {
  readonly id: number
  readonly provider: SocialProvider
  readonly email: string | null
  readonly displayName: string
  readonly lastLoginAt: string | null
}

export interface UserToken {
  readonly accessToken: string
  readonly tokenType: string
  readonly expiresAt: string
  readonly user: UserProfile
}

/**
 * 로그인 버튼 하나를 그리는 데 필요한 것 전부. **동의 화면 주소를 서버가 조립해 준다** — 그래서
 * 클라이언트에 client_id 가 없고, 제공자를 추가할 때 프론트엔드를 고치지 않는다.
 */
export interface SocialProviderOption {
  readonly provider: SocialProvider
  readonly authorizationUri: string
}

export const PROVIDER_LABELS: Readonly<Record<SocialProvider, string>> = {
  GOOGLE: 'Google 로 계속하기',
  KAKAO: 'Kakao 로 계속하기',
}

/** 콜백 주소는 인가 코드를 교환할 때 **같은 값**을 다시 보내야 한다. 한 곳에서 만든다. */
export function callbackUri(): string {
  return `${window.location.origin}/login/callback`
}

export function fetchProviders(state: string, signal?: AbortSignal): Promise<SocialProviderOption[]> {
  const query = new URLSearchParams({ redirectUri: callbackUri(), state })
  return getJson<SocialProviderOption[]>(`/api/auth/providers?${query}`, signal)
}

/**
 * 인가 코드를 서버에 넘긴다. 교환은 서버가 한다 — 클라이언트가 제공자 토큰을 직접 받으면 서버는 그
 * 토큰이 우리 앱을 위해 발급된 것인지 확인할 방법이 없다.
 */
export function exchangeCode(
  provider: SocialProvider,
  authorizationCode: string,
): Promise<UserToken> {
  return postJson<UserToken>(`/api/auth/social/${provider}`, {
    authorizationCode,
    redirectUri: callbackUri(),
  })
}

export function fetchMe(signal?: AbortSignal): Promise<UserProfile> {
  return getJson<UserProfile>('/api/auth/me', signal)
}
