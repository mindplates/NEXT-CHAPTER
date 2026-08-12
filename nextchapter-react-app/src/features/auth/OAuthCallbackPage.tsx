import { useMutation } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import type { SocialProvider } from './api/authApi'
import { exchangeCode } from './api/authApi'
import { clearIssuedState, readIssuedState } from './oauthState'
import { useAuthStore } from './store/authStore'

/**
 * 제공자가 돌려보낸 인가 코드를 서버에 넘긴다.
 *
 * `state` 를 대조하는 것이 요점이다. 없으면 다른 사이트가 시작한 로그인 흐름의 코드를 이 브라우저가
 * 받아 처리할 수 있고, 그러면 사용자가 남의 계정으로 로그인된다.
 *
 * `state` 는 `<임의 값>.<제공자>` 다. 제공자는 콜백에 자기 이름을 돌려주지 않으므로, 어차피 왕복하는
 * 이 값에 실어 보낸다 — 콜백 주소를 제공자마다 다르게 두면 제공자 콘솔에 주소를 하나 더 등록해야 한다.
 */
export function OAuthCallbackPage() {
  const [params] = useSearchParams()
  const signIn = useAuthStore((state) => state.signIn)
  const accessToken = useAuthStore((state) => state.accessToken)

  const code = params.get('code')
  const providerError = params.get('error')
  const { nonce, provider } = splitState(params.get('state'))
  const stateMatches = nonce !== null && nonce === readIssuedState()

  const login = useMutation({
    mutationFn: () => exchangeCode(provider as SocialProvider, code as string),
    onSuccess: (token) => {
      clearIssuedState()
      signIn(token.accessToken, token.user)
    },
  })

  const ready = Boolean(code) && provider !== null && stateMatches
  useEffect(() => {
    if (ready && login.isIdle) {
      login.mutate()
    }
  }, [ready, login])

  if (accessToken) {
    return <Navigate to="/" replace />
  }
  if (providerError) {
    return <Failure message={`제공자가 로그인을 거절했습니다 (${providerError}).`} />
  }
  if (!code || provider === null) {
    return <Failure message="인가 코드를 받지 못했습니다." />
  }
  if (!stateMatches) {
    // 사유를 자세히 알려 주지 않는다 — 정상 사용자에게는 "다시 시도"가 유일한 대응이다.
    return <Failure message="로그인 요청을 확인할 수 없습니다. 다시 시도해 주세요." />
  }
  if (login.isError) {
    return <Failure message={login.error.message} />
  }

  return <p className="page">로그인 중…</p>
}

function splitState(raw: string | null): {
  readonly nonce: string | null
  readonly provider: SocialProvider | null
} {
  if (!raw) {
    return { nonce: null, provider: null }
  }
  const separator = raw.lastIndexOf('.')
  if (separator < 0) {
    return { nonce: raw, provider: null }
  }
  return {
    nonce: raw.slice(0, separator),
    provider: raw.slice(separator + 1) as SocialProvider,
  }
}

function Failure({ message }: { readonly message: string }) {
  return (
    <section className="page page--narrow">
      <p role="alert">{message}</p>
      <Link to="/login">로그인으로 돌아가기</Link>
    </section>
  )
}
