import type { HealthStatus } from './api/healthApi'
import { useHealth } from './hooks/useHealth'

/** 저장소별로 사람이 읽는 이름. 헬스 응답의 컴포넌트 키와 맞춘다. */
const COMPONENT_LABELS: Readonly<Record<string, string>> = {
  db: 'PostgreSQL',
  neo4j: 'Neo4j',
  kafka: 'Kafka',
  redis: 'Redis',
  objectStorage: '오브젝트 스토리지',
}

function statusMark(status: HealthStatus): string {
  return status === 'UP' ? '●' : '○'
}

export function HealthPage() {
  const { data, isPending, isError, error } = useHealth()

  if (isPending) {
    return <p>확인 중…</p>
  }

  if (isError) {
    return <p role="alert">백엔드에 연결할 수 없습니다 — {error.message}</p>
  }

  const components = data.components ?? {}
  const tracked = Object.keys(COMPONENT_LABELS).filter((key) => key in components)

  return (
    <section>
      <h1>NEXT CHAPTER</h1>
      <p>
        백엔드 <strong>{data.status}</strong>
      </p>
      <ul>
        {tracked.map((key) => (
          <li key={key}>
            <span aria-hidden="true">{statusMark(components[key].status)}</span>{' '}
            {COMPONENT_LABELS[key]} — {components[key].status}
          </li>
        ))}
      </ul>
      <p>
        저장소 {tracked.filter((key) => components[key].status === 'UP').length} / {tracked.length} 연결됨
      </p>
    </section>
  )
}
