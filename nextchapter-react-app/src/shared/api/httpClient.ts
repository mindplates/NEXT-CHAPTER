/** 서버 응답이 실패했을 때 던지는 오류. 상태 코드를 보존해 호출부가 분기할 수 있게 한다. */
export class HttpError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'HttpError'
    this.status = status
  }
}

/**
 * 서버의 응답 봉투. 성공은 `data` 만, 실패는 `message`(+ 필드 오류)만 채워진다.
 */
export interface ApiResponse<T> {
  readonly data?: T
  readonly message?: string
  readonly errors?: ReadonlyArray<{ readonly field: string; readonly message: string }>
}

/**
 * 액세스 토큰 공급자. 인증 스토어가 등록한다.
 *
 * 스토어를 직접 import 하지 않는 이유는 순환 의존을 만들지 않기 위해서다 — 스토어는 API 를 부르고,
 * API 는 토큰을 필요로 한다. 함수 하나를 등록받으면 방향이 한쪽으로 유지된다.
 */
let accessTokenProvider: () => string | null = () => null

export function setAccessTokenProvider(provider: () => string | null): void {
  accessTokenProvider = provider
}

/**
 * 401 을 받았을 때 호출된다. 토큰 만료가 곧 로그아웃이다 — 리프레시 토큰을 두지 않기로 했으므로
 * 되살릴 방법이 없고, 만료된 토큰을 들고 계속 실패하는 것보다 로그인 화면으로 보내는 편이 낫다.
 */
let unauthorizedHandler: () => void = () => {}

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

function authHeaders(): Record<string, string> {
  const token = accessTokenProvider()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function unwrap<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    unauthorizedHandler()
  }

  const body = (await response.json().catch(() => ({}))) as ApiResponse<T>

  if (!response.ok) {
    // 서버가 준 메시지를 그대로 보여 준다. 마지막 핸들러가 내부 정보를 걸러 고정 문구를 주므로
    // 이 값이 사용자에게 노출돼도 안전하다.
    throw new HttpError(response.status, body.message ?? `요청이 실패했습니다 (${response.status})`)
  }

  return body.data as T
}

/** 봉투를 벗기지 않는 GET. actuator 처럼 봉투를 쓰지 않는 응답에 쓴다. */
export async function getRaw<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json', ...authHeaders() },
    signal,
  })

  if (!response.ok) {
    throw new HttpError(response.status, `요청이 실패했습니다 (${response.status})`)
  }

  return (await response.json()) as T
}

/**
 * JSON GET. 개발 중에는 Vite 프록시가 같은 오리진으로 백엔드에 넘긴다.
 *
 * 서버가 내리는 것은 블록 문서이지 HTML 이 아니므로 클라이언트는 항상 JSON 을 받는다.
 */
export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  return unwrap<T>(
    await fetch(path, {
      method: 'GET',
      headers: { Accept: 'application/json', ...authHeaders() },
      signal,
    }),
  )
}

export async function postJson<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  return unwrap<T>(
    await fetch(path, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...authHeaders(),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    }),
  )
}
