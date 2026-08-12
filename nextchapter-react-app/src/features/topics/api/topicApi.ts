import { getJson, postJson } from '../../../shared/api/httpClient'

export type TopicResolution =
  | 'MATCHED'
  | 'CANDIDATES_PRESENTED'
  | 'NEW_TOPIC_CREATED'
  | 'NEEDS_REVIEW'

export interface TopicCandidate {
  readonly topicId: number
  readonly name: string
  readonly fieldName: string
  readonly domainName: string
  readonly similarity: number
}

/**
 * 자유 입력 처리 결과.
 *
 * `requestId` 가 대화의 연결 고리다. 후보를 제시한 뒤 사용자의 판정을 받으려면 "무엇에 대한 판정인가"가
 * 서버에 남아 있어야 한다.
 */
export interface TopicResolutionResult {
  readonly requestId: number
  readonly resolution: TopicResolution
  readonly topicId: number | null
  readonly topicName: string | null
  readonly fieldName: string | null
  readonly domainName: string | null
  readonly skeletonExists: boolean
  readonly candidates: readonly TopicCandidate[]
  readonly canCreateNew: boolean
  readonly proposedTopicName: string | null
}

export interface ChapterNode {
  readonly chapterId: number
  readonly chapterKey: string
  readonly title: string
  readonly version: number
}

export interface SkeletonEntry {
  readonly skeletonId: number
  readonly topicId: number
  readonly entryPoints: readonly ChapterNode[]
}

export function resolveTopic(input: string): Promise<TopicResolutionResult> {
  return postJson<TopicResolutionResult>('/api/topics/resolve', { input })
}

export function confirmCandidate(requestId: number, topicId: number): Promise<TopicResolutionResult> {
  return postJson<TopicResolutionResult>(`/api/topics/resolve/${requestId}/confirm`, { topicId })
}

/** 후보 전부 거절. 붙일 분야가 있으면 신규 주제와 뼈대가 만들어진다 — 생성 비용의 방어선이다. */
export function rejectCandidates(requestId: number): Promise<TopicResolutionResult> {
  return postJson<TopicResolutionResult>(`/api/topics/resolve/${requestId}/reject`)
}

/**
 * 주제의 진입점. **published 뼈대만** 답하므로, 생성 중이면 404 다 — 그 사실이 "아직 준비되지 않았다"를
 * 화면에 표시하는 근거가 된다.
 */
export function fetchEntryPoints(topicId: number, signal?: AbortSignal): Promise<SkeletonEntry> {
  return getJson<SkeletonEntry>(`/api/topics/${topicId}/entry-points`, signal)
}
