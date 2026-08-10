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
 * JSON GET. 개발 중에는 Vite 프록시가 같은 오리진으로 백엔드에 넘긴다.
 *
 * 서버가 내리는 것은 블록 문서이지 HTML 이 아니므로 클라이언트는 항상 JSON 을 받는다.
 */
export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    signal,
  })

  if (!response.ok) {
    throw new HttpError(response.status, `요청이 실패했습니다 (${response.status})`)
  }

  return (await response.json()) as T
}
