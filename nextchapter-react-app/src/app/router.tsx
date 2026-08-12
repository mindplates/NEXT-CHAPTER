import { createBrowserRouter } from 'react-router-dom'
import { LoginPage } from '../features/auth/LoginPage'
import { OAuthCallbackPage } from '../features/auth/OAuthCallbackPage'
import { RequireAuth } from '../features/auth/RequireAuth'
import { ChapterPage } from '../features/chapters/ChapterPage'
import { HealthPage } from '../features/health/HealthPage'
import { TopicEntryPage } from '../features/topics/TopicEntryPage'
import { AppShell } from './AppShell'

/**
 * 사용자 경로는 하나다 — 로그인 → 주제 진입 → 그래프 탐색 → 챕터 소비.
 *
 * 관리자 화면은 여기 없다. 운영자 경로는 사용자 경로와 별개이고(인증 체인도 다르다), 한 라우터에 섞으면
 * 사용자 화면의 변경이 운영 화면을 흔든다.
 */
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/login/callback', element: <OAuthCallbackPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <TopicEntryPage /> },
      { path: 'chapters/:chapterId', element: <ChapterPage /> },
      // 헬스 화면은 M0 부터 있던 것이다. 저장소 5개가 떠 있는지 눈으로 확인하는 데 계속 쓴다.
      { path: 'health', element: <HealthPage /> },
    ],
  },
])
