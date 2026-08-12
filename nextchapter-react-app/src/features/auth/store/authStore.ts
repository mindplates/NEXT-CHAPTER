import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { setAccessTokenProvider, setUnauthorizedHandler } from '../../../shared/api/httpClient'
import type { UserProfile } from '../api/authApi'

/**
 * 인증 상태. **클라이언트 상태**이므로 Zustand 가 맡는다 — 서버에서 온 값을 TanStack Query 캐시에
 * 두고 따로 복사하면 그 둘을 맞추는 동기화 코드가 생긴다.
 *
 * 토큰을 localStorage 에 두는 이유는 리프레시 토큰이 없기 때문이다. 새로고침마다 다시 로그인하게
 * 만들면 7일 TTL 이 의미를 잃는다. 쿠키를 쓰지 않는 것은 서버 쪽 결정과 짝이다 — 브라우저가 자동
 * 전송하는 자격 증명을 만들지 않기로 했다.
 */
interface AuthState {
  readonly accessToken: string | null
  readonly user: UserProfile | null
  readonly signIn: (accessToken: string, user: UserProfile) => void
  readonly signOut: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      signIn: (accessToken, user) => set({ accessToken, user }),
      signOut: () => set({ accessToken: null, user: null }),
    }),
    { name: 'nextchapter-auth' },
  ),
)

// HTTP 클라이언트에 토큰 공급자를 등록한다. 클라이언트가 스토어를 직접 import 하면 순환이 된다.
setAccessTokenProvider(() => useAuthStore.getState().accessToken)

// 401 은 곧 로그아웃이다. 만료된 토큰을 들고 계속 실패하는 것보다 로그인 화면으로 보내는 편이 낫다.
setUnauthorizedHandler(() => useAuthStore.getState().signOut())
