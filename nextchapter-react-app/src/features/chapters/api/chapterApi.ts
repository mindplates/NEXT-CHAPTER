import { getJson, postJson } from '../../../shared/api/httpClient'

/**
 * 서버가 내리는 블록. **HTML 이 아니다** — 렌더링은 클라이언트가 하고, 그래서 새 플랫폼이 생겨도 이 API 가
 * 그대로다.
 *
 * `NARRATION` 은 웹 응답에 오지 않는다(영상·자막의 원천이다). 타입에 남겨 두는 이유는 같은 엔드포인트로
 * 다른 형태를 요청할 수 있기 때문이다.
 */
export type BlockType = 'HEADING' | 'PARAGRAPH' | 'FIGURE' | 'FORMULA' | 'NARRATION' | 'QUIZ'

export type DeliveryFormat = 'WEB' | 'VIDEO' | 'PRESENTATION'

export interface Block {
  readonly id: string
  readonly type: BlockType
  readonly text: string | null
  readonly attributes: Readonly<Record<string, unknown>>
}

/**
 * 챕터 하나. `version` 이 **사용자가 실제로 소비한 버전**이고, 신호를 남길 때 이 값을 그대로 돌려보낸다 —
 * 무조건 최신 버전으로 기록하면 구버전을 본 사람의 오답이 최신 버전의 실패로 잘못 집계된다.
 */
export interface Chapter {
  readonly chapterId: number
  readonly chapterKey: string
  readonly title: string
  readonly summary: string | null
  readonly version: number
  readonly format: DeliveryFormat
  readonly blocks: readonly Block[]
  readonly personalized: boolean
  readonly improvedFromPrevious: boolean
  readonly changeSummary: string | null
  readonly versionCreatedAt: string | null
}

export type RelationType = 'PREREQUISITE' | 'RELATED' | 'DEEPENS'

export interface ChapterNode {
  readonly chapterId: number
  readonly chapterKey: string
  readonly title: string
  readonly version: number
}

export interface Neighborhood {
  readonly center: ChapterNode
  readonly depth: number
  readonly neighbors: readonly ChapterNode[]
  readonly relations: ReadonlyArray<{
    readonly fromChapterId: number
    readonly toChapterId: number
    readonly relationType: RelationType
  }>
}

export function fetchChapter(chapterId: number, signal?: AbortSignal): Promise<Chapter> {
  return getJson<Chapter>(`/api/chapters/${chapterId}?format=WEB`, signal)
}

export function fetchNeighbors(chapterId: number, signal?: AbortSignal): Promise<Neighborhood> {
  return getJson<Neighborhood>(`/api/chapters/${chapterId}/neighbors`, signal)
}

export type SignalType = 'QUIZ_ANSWER' | 'QUESTION' | 'ERROR_REPORT' | 'PROGRESS'

/**
 * 신호 하나.
 *
 * 퀴즈는 **고른 답만** 보낸다. 정답 대조는 서버가 한다 — 클라이언트가 정답 여부를 계산하면 그 값은
 * 위조할 수 있고, 오답률은 집단 루프의 핵심 신호다.
 */
export interface RecordSignalRequest {
  readonly chapterVersion: number
  readonly blockId: string | null
  readonly format: DeliveryFormat
  readonly type: SignalType
  readonly payload: Readonly<Record<string, unknown>>
}

export function recordSignal(chapterId: number, request: RecordSignalRequest): Promise<unknown> {
  return postJson(`/api/chapters/${chapterId}/signals`, request)
}
