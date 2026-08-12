/**
 * OAuth `state` 의 임의 값. 콜백에서 대조해 <b>다른 사이트가 시작한 로그인 흐름</b>을 걸러 낸다.
 *
 * `sessionStorage` 에 두는 이유는 이 값의 수명이 한 번의 로그인 시도이기 때문이다. localStorage 에
 * 두면 탭을 닫아도 남아 오래된 값과 대조가 통과할 수 있다.
 *
 * 서버가 만들지 않는 이유는 저장소가 필요해지기 때문이다 — 이 값은 브라우저 안에서만 왕복하면 충분하다.
 */
const STATE_KEY = 'nextchapter-oauth-state'

export function issueState(): string {
  const existing = sessionStorage.getItem(STATE_KEY)
  if (existing) {
    return existing
  }
  const state = crypto.randomUUID()
  sessionStorage.setItem(STATE_KEY, state)
  return state
}

export function readIssuedState(): string | null {
  return sessionStorage.getItem(STATE_KEY)
}

export function clearIssuedState(): void {
  sessionStorage.removeItem(STATE_KEY)
}
