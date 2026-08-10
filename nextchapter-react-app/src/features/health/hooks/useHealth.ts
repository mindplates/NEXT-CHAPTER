import { useQuery } from '@tanstack/react-query'
import { fetchHealth } from '../api/healthApi'

export const HEALTH_QUERY_KEY = ['health'] as const

export function useHealth() {
  return useQuery({
    queryKey: HEALTH_QUERY_KEY,
    queryFn: ({ signal }) => fetchHealth(signal),
    refetchInterval: 10_000,
  })
}
