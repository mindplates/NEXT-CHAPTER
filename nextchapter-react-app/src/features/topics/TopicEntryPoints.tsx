import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { HttpError } from '../../shared/api/httpClient'
import { fetchEntryPoints } from './api/topicApi'

/**
 * 진입점 목록. 챕터는 선형 목차가 아니라 그래프이므로 "1강"이 아니라 **시작할 수 있는 지점들**이다.
 *
 * 404 를 "아직 준비되지 않았다"로 읽는다 — 서버는 published 뼈대만 답하고, 존재 여부를 나누지 않는다.
 */
export function TopicEntryPoints({ topicId }: { readonly topicId: number }) {
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['topics', topicId, 'entry-points'],
    queryFn: ({ signal }) => fetchEntryPoints(topicId, signal),
  })

  if (isPending) {
    return <p>진입점을 확인 중…</p>
  }

  if (isError) {
    const notReady = error instanceof HttpError && error.status === 404
    return (
      <p role="status">
        {notReady
          ? '이 주제는 아직 공개되지 않았습니다. 생성이 끝나면 열립니다.'
          : `진입점을 불러올 수 없습니다 — ${error.message}`}
      </p>
    )
  }

  if (data.entryPoints.length === 0) {
    // 선행 관계에 순환이 있으면 진입점이 사라진다. 생성 시점에 걸러지므로 정상 경로에서는 비지 않는다.
    return <p role="alert">시작할 수 있는 챕터가 없습니다.</p>
  }

  return (
    <div>
      <h2>여기서 시작할 수 있습니다</h2>
      <ul className="chapter-list">
        {data.entryPoints.map((node) => (
          <li key={node.chapterId}>
            <Link to={`/chapters/${node.chapterId}`}>{node.title}</Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
