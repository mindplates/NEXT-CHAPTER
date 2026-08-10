import { createBrowserRouter } from 'react-router-dom'
import { HealthPage } from '../features/health/HealthPage'

// M0 는 헬스체크 화면 하나뿐이다. 학습 경로(주제 → 그래프 → 챕터)는 M3 에서 붙는다.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <HealthPage />,
  },
])
