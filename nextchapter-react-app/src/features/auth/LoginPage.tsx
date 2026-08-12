import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { PROVIDER_LABELS, fetchProviders } from './api/authApi'
import { issueState } from './oauthState'
import { useAuthStore } from './store/authStore'

/**
 * 로그인 화면. **버튼 목록을 서버에서 받는다** — 하드코딩하면 설정되지 않은 제공자의 버튼이 남고,
 * 사용자는 누른 뒤에야 실패를 본다.
 */
export function LoginPage() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const state = issueState()
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['auth', 'providers', state],
    queryFn: ({ signal }) => fetchProviders(state, signal),
  })

  if (accessToken) {
    return <Navigate to="/" replace />
  }

  return (
    <section className="page page--narrow">
      <h1>NEXT CHAPTER</h1>
      <p>주제를 고르면 AI 가 전 범위를 만들고, 당신의 반응이 다음에 볼 챕터를 결정합니다.</p>

      {isPending && <p>로그인 방법을 확인 중…</p>}
      {isError && <p role="alert">로그인 방법을 불러올 수 없습니다 — {error.message}</p>}
      {data?.length === 0 && (
        <p role="alert">사용할 수 있는 로그인 제공자가 없습니다. 서버에 소셜 로그인 설정이 필요합니다.</p>
      )}

      <ul className="provider-list">
        {data?.map((option) => (
          <li key={option.provider}>
            {/* 동의 화면으로 나가는 것은 전체 페이지 이동이다 — 제공자 화면은 iframe 을 허용하지 않는다. */}
            <a className="button" href={option.authorizationUri}>
              {PROVIDER_LABELS[option.provider] ?? option.provider}
            </a>
          </li>
        ))}
      </ul>
    </section>
  )
}
