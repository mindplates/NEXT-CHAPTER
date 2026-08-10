# NEXT CHAPTER

AI가 주제별 학습 콘텐츠를 생성하고, **학습자의 반응이 그 콘텐츠를 고쳐 나가는** 학습 서비스.

- 제품 방향과 설계 → [`CLAUDE.md`](CLAUDE.md)
- 구현 순서와 진행 상태 → [`docs/`](docs/)

현재 단계는 **M0 — 기반 골격과 CI**다. 기능은 아직 없고 골격·로컬 환경·CI·헬스체크만 있다.

## 요구 사항

| | 버전 |
|---|---|
| JDK | 21 |
| Node | 22 |
| Docker | Compose v2 포함 |

## 실행

### 1. 저장소 5개 띄우기

```bash
docker compose up -d
```

| 서비스 | 로컬 포트 |
|--------|-----------|
| PostgreSQL (pgvector) | 34200 |
| Neo4j (Bolt / HTTP) | 34300 / 34400 |
| Kafka | 34500 |
| Redis | 34600 |
| MinIO (S3 API / 콘솔) | 34700 / 34701 |

기본 포트를 쓰지 않는 이유는 개발 머신에 이미 떠 있는 다른 프로젝트와 충돌하기 때문이다.
34000번대를 100 단위로 배정하고, 부가 포트는 자기 구간 안에서 해결한다(예: 백엔드 디버그 34001).

자격 증명 기본값은 [`.env.example`](.env.example)에 있다. 바꾸려면 `.env`로 복사해 수정한다.

### 2. 백엔드

```bash
./gradlew :nextchapter-bootstrap:bootRun
```

http://localhost:34000/actuator/health — 저장소 5개의 연결 상태가 함께 나온다.

```json
{"status":"UP","components":{"db":{"status":"UP"},"neo4j":{"status":"UP"},
 "kafka":{"status":"UP"},"redis":{"status":"UP"},"objectStorage":{"status":"UP"}}}
```

### 3. 프론트엔드

```bash
cd nextchapter-react-app
npm install
npm run dev
```

http://localhost:34100 — `/api`와 `/actuator`는 백엔드로 프록시된다.

## 빌드와 테스트

```bash
./gradlew build                      # 빌드 + 테스트 + 커버리지 + 의존 방향 검증
./gradlew checkModuleDependencies    # 헥사고날 의존 방향만 검증
```

어댑터 통합 테스트는 Testcontainers로 실제 저장소에 붙으므로 **Docker가 떠 있어야 한다.**
(랜덤 포트를 쓰므로 위 포트 표와 무관하고, `docker compose`가 떠 있을 필요는 없다.)

## 모듈 구조

헥사고날 아키텍처, 계층축 멀티모듈. 관례는 사내 WORKS 프로젝트를 따른다.

```
nextchapter-common          공용 유틸
nextchapter-domain          순수 도메인 (프레임워크 무의존)
nextchapter-application     유스케이스와 포트
nextchapter-adapter/
  web          REST                    (in)
  messaging    Kafka                   (in/out)
  persistence  PostgreSQL              (out)
  graph        Neo4j                   (out)
  cache        Redis                   (out)
  storage      오브젝트 스토리지        (out)
  ai           LLM 프로바이더           (out)
  media        TTS · 영상 렌더 · PPT    (out)
  security     인증 · 인가
nextchapter-bootstrap       조립
nextchapter-react-app       프론트엔드 (React + Vite)
```

의존 방향은 빌드가 강제한다 — `common ← domain ← application ← adapter/* ← bootstrap`.
어댑터끼리는 의존할 수 없다. 위반하면 `checkModuleDependencies`가 빌드를 실패시킨다.
