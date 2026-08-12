import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { HttpError } from '../../shared/api/httpClient'
import type { TopicResolutionResult } from './api/topicApi'
import { confirmCandidate, rejectCandidates, resolveTopic } from './api/topicApi'
import { TopicEntryPoints } from './TopicEntryPoints'

/**
 * 주제 입력 → 하나의 뼈대.
 *
 * 후보를 제시하고 **사용자가 판정한다.** 자동 생성으로 흘리면 오매칭 방지망이 없고, 운영자 승인
 * 대기열로 보내면 사용자가 그 자리에서 학습을 시작할 수 없다. 이 확인 단계가 동시에 신규 뼈대 생성
 * 비용의 방어선이다.
 */
export function TopicEntryPage() {
  const [input, setInput] = useState('')
  const [result, setResult] = useState<TopicResolutionResult | null>(null)

  const resolve = useMutation({
    mutationFn: () => resolveTopic(input.trim()),
    onSuccess: setResult,
  })
  const confirm = useMutation({
    mutationFn: (topicId: number) => confirmCandidate(result!.requestId, topicId),
    onSuccess: setResult,
  })
  const reject = useMutation({
    mutationFn: () => rejectCandidates(result!.requestId),
    onSuccess: setResult,
  })

  const pending = resolve.isPending || confirm.isPending || reject.isPending
  const failure = resolve.error ?? confirm.error ?? reject.error

  return (
    <section className="page">
      <h1>무엇을 배우고 싶나요?</h1>

      <form
        className="topic-form"
        onSubmit={(event) => {
          event.preventDefault()
          if (input.trim() && !pending) {
            resolve.mutate()
          }
        }}
      >
        <input
          aria-label="배우고 싶은 주제"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="예: 머신러닝"
          maxLength={200}
        />
        <button className="button" type="submit" disabled={pending || !input.trim()}>
          찾기
        </button>
      </form>

      {pending && <p>확인 중…</p>}
      {failure && <ResolveError error={failure} />}

      {result && <Outcome result={result} onConfirm={confirm.mutate} onReject={() => reject.mutate()} />}
    </section>
  )
}

/**
 * 429 를 따로 다룬다. 이 엔드포인트는 호출 하나가 LLM 을 최대 3번 부르므로 리밋이 걸려 있고, 그 사실을
 * 알려 주지 않으면 사용자는 계속 눌러 한도를 더 소진한다.
 */
function ResolveError({ error }: { readonly error: Error }) {
  const tooMany = error instanceof HttpError && error.status === 429
  return (
    <p role="alert">{tooMany ? error.message : `주제를 찾지 못했습니다 — ${error.message}`}</p>
  )
}

function Outcome({
  result,
  onConfirm,
  onReject,
}: {
  readonly result: TopicResolutionResult
  readonly onConfirm: (topicId: number) => void
  readonly onReject: () => void
}) {
  if (result.resolution === 'MATCHED' || result.resolution === 'NEW_TOPIC_CREATED') {
    return <Matched result={result} />
  }

  if (result.resolution === 'CANDIDATES_PRESENTED') {
    return (
      <div>
        <p>혹시 이 주제인가요?</p>
        <ul className="candidate-list">
          {result.candidates.map((candidate) => (
            <li key={candidate.topicId}>
              <button className="button button--quiet" onClick={() => onConfirm(candidate.topicId)}>
                {candidate.domainName} › {candidate.fieldName} › <strong>{candidate.name}</strong>
              </button>
            </li>
          ))}
        </ul>
        <button className="button button--quiet" onClick={onReject}>
          {result.canCreateNew ? '아니요, 새 주제로 시작하기' : '아니요, 모두 다릅니다'}
        </button>
      </div>
    )
  }

  // NEEDS_REVIEW — 붙일 분야가 없어 운영자 검토로 갔다. 신규 도메인·분야는 자동으로 만들지 않는다.
  return <p role="status">아직 다루지 않는 주제입니다. 검토 목록에 올렸습니다.</p>
}

function Matched({ result }: { readonly result: TopicResolutionResult }) {
  return (
    <div>
      <p>
        <strong>{result.topicName}</strong>
        {result.fieldName && ` (${result.domainName} › ${result.fieldName})`}
      </p>
      {result.skeletonExists ? (
        <TopicEntryPoints topicId={result.topicId!} />
      ) : (
        <p role="status">
          이 주제의 콘텐츠를 만들고 있습니다. 전 범위를 한 번에 만들기 때문에 시간이 걸립니다 — 완료되면
          알려 드립니다.
        </p>
      )}
      <p>
        <Link to="/">다른 주제 찾기</Link>
      </p>
    </div>
  )
}
