import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 로컬 포트는 34000번대를 100 단위로 배정한다 (CLAUDE.md → 로컬 개발 환경).
const BACKEND_ORIGIN = 'http://localhost:34000'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 34100,
    strictPort: true,
    // 개발 중 백엔드를 같은 오리진으로 보이게 해 CORS 설정을 만들지 않는다.
    proxy: {
      '/api': { target: BACKEND_ORIGIN, changeOrigin: true },
      '/actuator': { target: BACKEND_ORIGIN, changeOrigin: true },
    },
  },
})
