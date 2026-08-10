# M0 — 기반 골격과 CI

| | |
|---|---|
| 상태 | ⬜ 예정 |
| 페이즈 | 0 / 6 |
| 선행 | 없음 |
| 후행 | [M1 카탈로그·주제 매핑](../m1-catalog/) |
| 설계 근거 | `CLAUDE.md` → Architecture → 모듈 구조 / 로컬 개발 환경 / 구현 순서 |

## 목표

**기능을 하나도 만들지 않는다.** 멀티모듈 골격, 로컬 저장소, CI, 헬스체크만 세운다.

기능 없는 단계를 하나 쓰는 대신 이후 모든 작업이 같은 바닥 위에서 시작한다. 반대로 이걸 뒤로
미루면 첫 기능이 임시 구조 위에 올라가고, 그 구조를 나중에 걷어내는 비용이 붙는다.

## 완료 기준 (DoD)

- [ ] `docker compose up` 으로 저장소 5개가 전부 뜬다
- [ ] `./gradlew build` 가 전 모듈에서 통과한다
- [ ] `http://localhost:34000` 헬스체크가 저장소 5개 연결 상태를 포함해 200을 반환한다
- [ ] CI가 push마다 빌드·테스트·커버리지를 돌리고 초록이다
- [ ] 모듈 의존 방향 위반이 빌드 실패로 잡힌다

## 페이즈

### P0.1 Gradle 멀티모듈 골격

- [ ] `settings.gradle` — WORKS 관례대로 모듈 선언
- [ ] `nextchapter-common` / `-domain` / `-application` / `-bootstrap`
- [ ] `nextchapter-adapter:{web, messaging, persistence, graph, cache, storage, ai, media, security}`
- [ ] **의존 방향 강제** — domain은 어디에도 의존하지 않고, adapter는 application을 향해서만 의존한다

> 의존 방향 강제를 나중에 넣으면 그때는 이미 깨져 있다. 골격을 세우는 이 시점이 유일하게 싼
> 타이밍이다.

### P0.2 패키지 스켈레톤

- [ ] `com.mindplates.nextchapter.domain.{도메인}.{model, exception}`
- [ ] `...application.{도메인}.{service, view, port.in, port.out, port.in.command}`
- [ ] `...adapter.in.web.{도메인}`, `...adapter.out.persistence.{도메인}`, `...adapter.out.graph.{도메인}`
- [ ] 도메인 축 7개: `catalog / skeleton / chapter / layer / signal / generation / admin`

도메인 축은 **패키지로만** 구분한다. 경계가 아직 검증되지 않았으므로 모듈로 굳히지 않는다.

### P0.3 docker-compose

- [ ] PostgreSQL (pgvector) → **34200**
- [ ] Neo4j Bolt **34300** / HTTP **34400**
- [ ] Kafka → **34500**
- [ ] Redis → **34600**
- [ ] 오브젝트 스토리지 (MinIO 등) → **34700**
- [ ] 초기 스키마·계정 부트스트랩 스크립트

오브젝트 스토리지는 M6까지 쓰지 않지만 지금 띄운다. compose 파일을 나중에 다시 여는 것보다 싸고,
포트 배정이 한 번에 끝난다.

### P0.4 부트스트랩 + 헬스체크

- [ ] 백엔드 **34000** (디버그 포트 34001)
- [ ] Spring Boot Actuator 헬스체크
- [ ] 저장소 5개 연결 상태를 헬스 응답에 포함

### P0.5 CI 파이프라인

- [ ] 빌드 · 테스트 · 커버리지 리포트
- [ ] 커버리지 기준 80% (프로젝트 규칙)
- [ ] Testcontainers가 CI에서 동작하는지 확인 — 랜덤 포트라 위 포트 표와 무관

### P0.6 프론트엔드 골격

- [ ] React 프로젝트 생성, dev 서버 **34100**
- [ ] 상태 관리 · 라우팅 · 디렉터리 규칙 확정
- [ ] 백엔드 헬스체크를 호출하는 화면 하나로 연결 확인

## 여기서 정해야 할 것

| 항목 | 비고 |
|------|------|
| 프론트엔드 구조 | 상태 관리 라이브러리, 라우팅, 디렉터리 규칙 — P0.6에서 확정 |
| Java 버전 · 빌드 툴 세부 | WORKS와 맞출지 여부 |

## 남은 정리 작업

- [ ] **ecc 훅의 Java 전환** — 현재 edit 훅이 TypeScript 전제(Prettier, `tsc`)다. 백엔드가
      Spring Boot/Java이므로 포매터 + 컴파일 체크로 교체해야 한다. 포크의 `hooks/hooks.json`
      수정 건이라 이 저장소 밖 작업이다.

## 리스크

| 리스크 | 대응 |
|--------|------|
| 저장소 5개를 로컬에서 동시에 띄우는 부담 | compose 프로파일로 필요한 것만 띄울 수 있게 분리 |
| Testcontainers가 CI 환경(Docker-in-Docker)에서 실패 | P0.5에서 실제로 한 번 돌려 확인 — M2 이후에 발견하면 전 어댑터 테스트가 막힌다 |
