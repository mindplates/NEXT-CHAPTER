-- ---------------------------------------------------------------------------
-- V13 — 일반 사용자 (소셜 로그인)
--
-- 관리자(V2 admin_accounts)와 테이블을 나눈다. 인증 경로를 나눈 이유가 그대로
-- 스키마에도 적용된다 — 운영자 접근이 외부 제공자 장애에 종속되면 안 되므로 관리자는
-- 자체 계정이고, 여기에는 비밀번호 컬럼이 아예 없다.
--
-- 비밀번호 컬럼을 두지 않는 것이 설계다. 공개 서비스라 진입장벽을 낮춰야 하고, 비밀번호
-- 저장·재설정·유출 대응을 떠안지 않기로 했다. 컬럼을 "일단 nullable 로" 두면 언젠가 그
-- 경로가 생기고, 그때는 이 결정이 무엇이었는지 아무도 모른다.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id               BIGSERIAL    PRIMARY KEY,
    provider         VARCHAR(40)  NOT NULL,
    -- 제공자가 준 안정 식별자(Google 의 sub, Kakao 의 id). 이메일이 아니다 —
    -- 사용자가 제공자 쪽에서 이메일을 바꾸면 같은 사람이 다른 계정이 된다.
    provider_user_id VARCHAR(200) NOT NULL,
    -- 제공자가 이메일을 주지 않을 수 있다(Kakao 는 동의 항목이다). NOT NULL 로 두면
    -- 로그인 자체가 실패하고, 그 실패는 "동의를 안 했다"가 아니라 서버 오류로 보인다.
    email            VARCHAR(200),
    display_name     VARCHAR(120) NOT NULL,
    last_login_at    TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    -- 정체성은 (제공자, 제공자 식별자)다. 이 UNIQUE 가 "같은 사람이 두 계정으로 갈라지지
    -- 않는다"를 지키는 실제 수단이다 — 애플리케이션 조회만 두면 동시 로그인 두 건이
    -- 사용자를 두 개 만들고, 그 순간 그 사람의 학습 레이어가 반으로 갈린다.
    CONSTRAINT uq_users_provider_identity UNIQUE (provider, provider_user_id)
);

-- 이메일에 UNIQUE 를 걸지 않는다. 같은 사람이 Google 과 Kakao 로 각각 들어올 수 있고,
-- 그때 두 번째 로그인이 실패하는 것은 받아들일 수 없다. 계정 연결은 별개 기능이고,
-- 필요해지면 그때 명시적으로 만든다 — 이메일 일치를 근거로 자동 병합하면 제공자가
-- 미검증 이메일을 준 경우 계정 탈취가 된다.
CREATE INDEX idx_users_email ON users (email);
