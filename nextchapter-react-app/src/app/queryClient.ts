import { QueryClient } from '@tanstack/react-query'

/**
 * 서버 상태는 TanStack Query 가, 클라이언트 상태는 Zustand 가 맡는다.
 * 둘을 섞으면 서버에서 온 값을 수동으로 동기화하는 코드가 생긴다.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
