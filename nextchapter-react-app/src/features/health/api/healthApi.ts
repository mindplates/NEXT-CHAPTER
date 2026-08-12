import { getRaw } from '../../../shared/api/httpClient'

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface HealthComponent {
  readonly status: HealthStatus
  readonly details?: Readonly<Record<string, unknown>>
}

export interface HealthResponse {
  readonly status: HealthStatus
  readonly components?: Readonly<Record<string, HealthComponent>>
}

/** 저장소 5개(PostgreSQL · Neo4j · Kafka · Redis · 오브젝트 스토리지)의 연결 상태가 여기 담긴다. */
export function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return getRaw<HealthResponse>('/actuator/health', signal)
}
