import { Link, Outlet } from 'react-router-dom'
import { useAuthStore } from '../features/auth/store/authStore'

/** 로그인한 사용자의 공통 껍데기. 헤더 하나와 자리 하나 — 화면 구성은 각 페이지가 한다. */
export function AppShell() {
  const user = useAuthStore((state) => state.user)
  const signOut = useAuthStore((state) => state.signOut)

  return (
    <div className="shell">
      <header className="shell__header">
        <Link className="shell__brand" to="/">
          NEXT CHAPTER
        </Link>
        <span className="shell__user">
          {user?.displayName}
          <button className="button button--tiny button--quiet" onClick={signOut}>
            로그아웃
          </button>
        </span>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
