-- ---------------------------------------------------------------------------
-- V2 — 관리자 계정
--
-- 사용자(소셜 로그인)와 별도 테이블이다. 관리자 접근이 외부 제공자 장애에 종속되면
-- 안 되기 때문이다 — 소셜 제공자가 죽었을 때 승인 대기열과 비용 상한 설정에 손댈 수
-- 없는 상태는 받아들일 수 없다.
--
-- role 을 지금부터 컬럼으로 두는 이유: 값은 ADMIN 하나뿐이지만(검수자·운영자 분리는
-- 아직 미결정), 나중에 컬럼을 추가하면 기존 행에 무엇을 채울지 다시 판단해야 한다.
-- ---------------------------------------------------------------------------
CREATE TABLE admin_accounts (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(200) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    role          VARCHAR(40)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_admin_accounts_email UNIQUE (email)
);
