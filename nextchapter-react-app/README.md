# nextchapter-react-app

NEXT CHAPTER 프론트엔드. React + Vite + TypeScript.

```bash
npm install
npm run dev      # http://localhost:34100
npm run lint
npm run build
```

`/api`와 `/actuator`는 백엔드(`localhost:34000`)로 프록시된다. 백엔드를 먼저 띄워야 화면이 채워진다.

## 구조

```
src/
  app/        앱 조립 — 라우터, QueryClient
  features/   기능 단위. 도메인별로 나눈다
  shared/     여러 기능이 함께 쓰는 것만
```

- **서버 상태**는 TanStack Query, **클라이언트 상태**는 Zustand. 서버 값을 스토어에 복사하지 않는다
- 서버가 내리는 것은 **블록 문서**이지 HTML이 아니다. 렌더는 이쪽 책임이다

전체 실행 방법과 설계 배경은 저장소 루트의 [`README.md`](../README.md)와 [`CLAUDE.md`](../CLAUDE.md)에 있다.
