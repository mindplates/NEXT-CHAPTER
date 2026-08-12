import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from './store/authStore'

/**
 * 로그인하지 않은 사용자를 로그인 화면으로 보낸다.
 *
 * 서버가 이미 401 로 막으므로 이것은 편의다 — 화면이 먼저 걸러 주면 사용자가 빈 화면과 오류 문구를
 * 보지 않는다. <b>서버 검사를 대체하지는 않는다.</b>
 */
export function RequireAuth({ children }: { readonly children: ReactNode }) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const location = useLocation()

  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
