# M0 — 기반 골격과 CI

| | |
|---|---|
| 상태 | ✅ 완료 |
| 페이즈 | 6 / 6 |
| 선행 | 없음 |
| 후행 | [M1 카탈로그·주제 매핑](../m1-catalog/) |
| 설계 근거 | `CLAUDE.md` → Architecture → 모듈 구조 / 로컬 개발 환경 / 구현 순서 |

## 목표

**기능을 하나도 만들지 않는다.** 멀티모듈 골격, 로컬 저장소, CI, 헬스체크만 세운다.

기능 없는 단계를 하나 쓰는 대신 이후 모든 작업이 같은 바닥 위에서 시작한다. 반대로 이걸 뒤로
미루면 첫 기능이 임시 구조 위에 올라가고, 그 구조를 나중에 걷어내는 비용이 붙는다.

## 완료 기준 (DoD)

- [x] `docker compose up` 으로 저장소 5개가 전부 뜬다
- [x] `./gradlew build` 가 전 모듈에서 통과한다
- [x] `http://localhost:34000/actuator/health` 가 저장소 5개 연결 상태를 포함해 200을 반환한다
- [x] CI가 push마다 빌드·테스트·커버리지를 돌린다
- [x] 모듈 의존 방향 위반이 빌드 실패로 잡힌다 (실제 위반을 넣어 검증함)

## 페이즈

### P0.1 Gradle 멀티모듈 골격 ✅

- [x] `settings.gradle` — WORKS 관례대로 모듈 선언 (Groovy DSL, Gradle 9.2.1, Java 21, Spring Boot 4.0.6)
- [x] `nextchapter-common` / `-domain` / `-application` / `-bootstrap`
- [x] `nextchapter-adapter:{web, messaging, persistence, graph, cache, storage, ai, media, security}`
- [x] **의존 방향 강제** — `checkModuleDependencies` 태스크가 `check` 에 물려 있다

허용 관계는 `common ← domain ← application ← adapter/* ← bootstrap` 이고 **어댑터끼리는 의존할
수 없다.** domain 이 common 에 의존하는 것은 WORKS 관례 그대로다.

> 의존 방향 강제를 나중에 넣으면 그때는 이미 깨져 있다. 골격을 세우는 이 시점이 유일하게 싼
> 타이밍이다. 통과만 하는 검사는 의미가 없으므로 실제 위반(domain → application)을 넣어
> 빌드가 실패하는 것까지 확인했다.

### P0.2 패키지 스켈레톤 ✅

- [x] `com.mindplates.nextchapter.domain.{도메인}` — 도메인 축 7개
- [x] `...application.{도메인}` — 도메인 축 7개
- [x] 어댑터 모듈별 베이스 패키지 — `adapter.in.web`, `adapter.out.{persistence, graph, cache, storage, ai, media, messaging}`, `adapter.security`
- [x] 도메인 축: `catalog / skeleton / chapter / layer / signal / generation / admin`

각 패키지에 `package-info.java` 를 둬 규칙을 문서화했다. 빈 디렉터리는 git 이 추적하지 못해
스켈레톤이 남지 않는다. 하위 세부 패키지(`model`, `exception`, `port.in` 등)는 첫 클래스가
생길 때 만든다 — 지금 만들면 24개가 아니라 100개가 넘는다.

### P0.3 docker-compose ✅

- [x] PostgreSQL (pgvector) → **34200**
- [x] Neo4j Bolt **34300** / HTTP **34400**
- [x] Kafka (KRaft 단일 노드) → **34500**
- [x] Redis → **34600**
- [x] MinIO S3 API **34700** / 콘솔 **34701**
- [x] pgvector 확장 생성 스크립트 · MinIO 자산 버킷 생성

MinIO 콘솔을 34701에 둔 것이 포트 규칙의 첫 적용 사례다 — 부가 포트를 자기 구간 안에서 해결한다.

오브젝트 스토리지는 M6까지 쓰지 않지만 지금 띄운다. compose 파일을 나중에 다시 여는 것보다 싸고,
포트 배정이 한 번에 끝난다.

### P0.4 부트스트랩 + 헬스체크 ✅

- [x] 백엔드 **34000**
- [x] Spring Boot Actuator 헬스체크
- [x] 저장소 5개 연결 상태를 헬스 응답에 포함
- [x] JVM 기본 시간대를 UTC 로 고정

`db` · `neo4j` · `redis` 는 Boot 자동설정이 제공하고, **Kafka 와 오브젝트 스토리지는 직접 만들었다.**
Kafka 는 `KafkaAdmin` 으로 클러스터를 describe 하고, 오브젝트 스토리지는 SDK 없이 헬스 경로를
HTTP 로 확인한다 — 스토리지 수단이 M6 의 미결정 항목이라 지금 SDK 를 고르면 코드가 그 결정을
먼저 해버린다.

### P0.5 CI 파이프라인 ✅

- [x] 빌드 · 테스트 · 커버리지 리포트 (GitHub Actions)
- [x] 의존 방향 검증을 CI 단계로 분리
- [x] 프론트엔드 lint · build 잡
- [x] Testcontainers 동작 확인 — pgvector 컨테이너를 띄워 확장 설치까지 검증하는 스모크 테스트

커버리지 **80% 게이트는 아직 걸지 않았다.** M0 는 기능이 없어 게이트를 걸면 첫날부터 빨간불이
된다. 리포트는 매 빌드 생성되고, 게이트는 첫 도메인 로직이 들어오는 M1에서 건다.

### P0.6 프론트엔드 골격 ✅

- [x] React + Vite + TypeScript, dev 서버 **34100**
- [x] `/api` · `/actuator` 를 백엔드로 프록시 — 개발 중 CORS 설정을 만들지 않기 위해
- [x] 상태 관리 · 라우팅 · 디렉터리 규칙 확정
- [x] 백엔드 헬스체크를 호출하는 화면으로 연결 확인

| 항목 | 선택 |
|------|------|
| 서버 상태 | TanStack Query |
| 클라이언트 상태 | Zustand |
| 라우팅 | React Router |
| 디렉터리 | `src/features/<도메인>/`, 공용은 `src/shared/`, 앱 조립은 `src/app/` |

상태를 둘로 나눈 이유는 서버에서 온 값을 클라이언트 스토어에 복사하기 시작하면 동기화 코드가
생기기 때문이다. 디렉터리를 타입이 아니라 기능 기준으로 나누는 것은 프로젝트 코딩 규칙 그대로다.

## 여기서 정한 것

| 항목 | 결정 |
|------|------|
| 프론트엔드 구조 | TanStack Query + Zustand + React Router, feature 기준 디렉터리 |
| Java · 빌드 툴 | WORKS 그대로 — Gradle 9.2.1 (Groovy DSL), Java 21, Spring Boot 4.0.6, `com.mindplates` |

## 남은 정리 작업

- [ ] **ecc 훅의 Java 전환** — 현재 edit 훅이 TypeScript 전제(Prettier, `tsc`)다. 백엔드가
      Spring Boot/Java이므로 포매터 + 컴파일 체크로 교체해야 한다. 포크의 `hooks/hooks.json`
      수정 건이라 이 저장소 밖 작업이다.
- [ ] **커버리지 80% 게이트** — 리포트는 나오지만 게이트는 아직 없다. M1에서 건다.
- [x] **CI 실측** — 원격에서 초록. Testcontainers 가 러너에서 실제로 컨테이너를 띄워
      pgvector 확장까지 검증하는 것을 확인했다. 이 과정에서 `.gitignore` 결함을 잡았다.

## 구현 중 걸린 것

기록해 두지 않으면 다음 사람이 같은 곳에서 멈춘다.

| 증상 | 원인 | 대응 |
|------|------|------|
| Kafka 컨테이너가 `advertised.listeners cannot use the nonroutable meta-address 0.0.0.0` 로 죽음 | `KAFKA_LISTENERS` 에 `0.0.0.0` 을 문자로 씀 | 호스트를 비운다 — `PLAINTEXT://:9092` |
| Kafka 가 스토리지 포맷 단계에서 죽음 | `CLUSTER_ID` 가 임의 문자열 | base64 UUID 22자 |
| `KafkaAdmin` 빈 없음 | Boot 4 는 자동설정을 기술별 모듈로 분리 | `spring-kafka` → `spring-boot-starter-kafka` |
| `org.testcontainers:postgresql` 해석 실패 | Testcontainers 2.x 가 아티팩트를 `testcontainers-*` 로 개명, 클래스도 `org.testcontainers.postgresql` 로 이동 | 새 좌표·패키지로 교체 |
| 프론트 빌드가 `erasableSyntaxOnly` 로 실패 | 생성자 파라미터 프로퍼티 사용 | 필드를 명시적으로 선언 |
| **`adapter/out/` 소스 11개가 커밋에서 통째로 빠짐** | `.gitignore` 의 앵커 없는 `out/` 규칙이 IDE 출력 디렉터리뿐 아니라 **헥사고날 out 어댑터 패키지 전체**를 매칭 | `/out/` 으로 루트 고정 |

마지막 항목은 로컬에서 전혀 드러나지 않았다. 파일이 디스크에 있으니 빌드도 헬스체크도 전부
통과했고, **원격 CI 가 `persistence:test NO-SOURCE` 를 찍고서야** 저장소에 소스가 없다는 것이
보였다. P0.5의 "CI 실측"이 형식적인 확인이 아니었던 이유다 — 로컬 성공과 저장소 상태는 다른
것이고, 그 차이는 조용히 틀린다.

## 리스크

| 리스크 | 대응 |
|--------|------|
| 저장소 5개를 로컬에서 동시에 띄우는 부담 | 필요해지면 compose 프로파일로 분리. 현재는 5개 다 필요 |
| Testcontainers가 CI 환경(Docker-in-Docker)에서 실패 | 로컬에서는 통과. **원격 CI 확인은 아직** — 위 정리 작업 참조 |
