import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { Link, useParams } from 'react-router-dom'
import { HttpError } from '../../shared/api/httpClient'
import { fetchChapter, requestSupplement } from './api/chapterApi'
import { BlockRenderer } from './blocks/BlockRenderer'
import { BlockSignalTools } from './blocks/BlockSignalTools'
import { useChapterSignals } from './hooks/useChapterSignals'
import { NeighborPanel } from './NeighborPanel'

/**
 * 챕터 소비 화면.
 *
 * 서버가 내리는 것은 **블록 목록**이므로 이 컴포넌트가 하는 일은 타입별 렌더와 신호 붙이기다. HTML 을
 * 서버가 만들면 이 화면은 훨씬 단순해지지만, 그 순간 모바일·태블릿 앱이 API 를 다시 요구한다.
 */
export function ChapterPage() {
  const { chapterId } = useParams()
  const id = Number(chapterId)

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['chapters', id],
    queryFn: ({ signal }) => fetchChapter(id, signal),
    enabled: Number.isFinite(id),
  })

  const signals = useChapterSignals(data)
  const queryClient = useQueryClient()

  /**
   * 보충 설명 요청. 성공하면 챕터를 다시 읽는다 — 합성 결과에 보충 블록이 이미 끼워져 오므로,
   * 응답으로 받은 블록을 화면에 따로 꽂지 않는다. 두 경로로 그리면 새로고침 후 위치가 달라질 수 있다.
   */
  const supplement = useMutation({
    mutationFn: (blockId: string) => requestSupplement(id, blockId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['chapters', id] }),
  })

  // 열었다는 사실 자체가 신호다. 챕터가 바뀔 때 한 번만 보낸다 — 렌더마다 보내면 진행률이 부풀려진다.
  const openedRef = useRef<number | null>(null)
  useEffect(() => {
    if (data && openedRef.current !== data.chapterId) {
      openedRef.current = data.chapterId
      signals.reportProgress(0)
    }
  }, [data, signals])

  if (!Number.isFinite(id)) {
    return <p role="alert" className="page">잘못된 챕터 주소입니다.</p>
  }
  if (isPending) {
    return <p className="page">불러오는 중…</p>
  }
  if (isError) {
    const missing = error instanceof HttpError && error.status === 404
    return (
      <section className="page">
        <p role="alert">
          {missing ? '이 챕터는 아직 공개되지 않았습니다.' : `챕터를 불러올 수 없습니다 — ${error.message}`}
        </p>
        <Link to="/">주제 찾기로</Link>
      </section>
    )
  }

  return (
    <article className="page chapter">
      <header>
        <h1>{data.title}</h1>
        {data.summary && <p className="chapter__summary">{data.summary}</p>}
        {/* 개선 배지. 재방문 자체가 개선 효과를 재는 신호가 된다. */}
        {data.improvedFromPrevious && (
          <p className="chapter__badge" role="status">
            이 챕터는 개선되었습니다{data.changeSummary ? ` — ${data.changeSummary}` : ''}
          </p>
        )}
        <p className="chapter__meta">v{data.version}</p>
      </header>

      {data.blocks.map((block) => (
        <BlockRenderer
          key={block.id}
          block={block}
          onAnswer={signals.answerQuiz}
          quizResult={signals.quizResults[block.id]}
        >
          <BlockSignalTools
            blockId={block.id}
            onQuestion={signals.askQuestion}
            onErrorReport={signals.reportError}
            // 보충 블록에는 보충을 붙이지 않는다. 서버도 거절하지만 버튼을 보여 주지 않는 편이 낫다.
            onSupplement={block.id.startsWith('b') ? supplement.mutate : undefined}
            supplementPending={supplement.isPending && supplement.variables === block.id}
          />
        </BlockRenderer>
      ))}

      {signals.isFailed && <p role="alert">기록을 보내지 못했습니다 — {signals.error?.message}</p>}
      {supplement.isError && <p role="alert">보충 설명을 만들지 못했습니다 — {supplement.error.message}</p>}

      <footer className="chapter__footer">
        <button className="button" onClick={() => signals.reportProgress(100)}>
          다 읽었습니다
        </button>
      </footer>

      <NeighborPanel chapterId={id} />
    </article>
  )
}
