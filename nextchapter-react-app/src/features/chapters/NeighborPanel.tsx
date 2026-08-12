import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import type { RelationType } from './api/chapterApi'
import { fetchNeighbors } from './api/chapterApi'

const RELATION_LABELS: Readonly<Record<RelationType, string>> = {
  PREREQUISITE: '먼저 볼 것',
  RELATED: '함께 볼 것',
  DEEPENS: '더 깊이',
}

/**
 * 챕터 주변. **선형 "다음 강의"가 아니다** — 챕터는 그래프이고, 사용자는 어느 방향으로든 움직인다.
 *
 * 관계 타입을 함께 보여 주는 이유는 그것이 이동의 근거이기 때문이다. "먼저 볼 것"과 "더 깊이"를 같은
 * 목록에 섞으면 그래프로 둔 이유가 사라진다.
 */
export function NeighborPanel({ chapterId }: { readonly chapterId: number }) {
  const { data, isPending, isError } = useQuery({
    queryKey: ['chapters', chapterId, 'neighbors'],
    queryFn: ({ signal }) => fetchNeighbors(chapterId, signal),
  })

  if (isPending) {
    return <aside className="neighbors">연관 챕터를 확인 중…</aside>
  }
  if (isError) {
    return <aside className="neighbors">연관 챕터를 불러올 수 없습니다.</aside>
  }

  const titles = new Map(data.neighbors.map((node) => [node.chapterId, node]))

  return (
    <aside className="neighbors">
      <h2>여기서 갈 수 있는 곳</h2>
      {data.neighbors.length === 0 && <p>연결된 챕터가 없습니다.</p>}
      <ul>
        {data.relations.map((relation) => {
          const otherId = relation.fromChapterId === chapterId ? relation.toChapterId : relation.fromChapterId
          const other = titles.get(otherId)
          if (!other) {
            return null
          }
          // 방향을 문구로 구분한다 — 이 챕터가 선행이면 "다음", 상대가 선행이면 "먼저 볼 것"이다.
          const incoming = relation.toChapterId === chapterId
          return (
            <li key={`${relation.fromChapterId}-${relation.toChapterId}-${relation.relationType}`}>
              <span className="neighbors__relation">
                {relation.relationType === 'PREREQUISITE' && !incoming
                  ? '이어서'
                  : RELATION_LABELS[relation.relationType]}
              </span>
              <Link to={`/chapters/${other.chapterId}`}>{other.title}</Link>
            </li>
          )
        })}
      </ul>
    </aside>
  )
}
