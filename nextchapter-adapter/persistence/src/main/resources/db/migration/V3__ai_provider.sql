-- ---------------------------------------------------------------------------
-- V3 — AI 프로바이더 설정 (단계별 벤더·모델) + 벤더별 API 키
--
-- 모델을 코드에 박지 않는다. 단계마다 벤더와 모델을 관리 화면에서 지정하고, 호출부는
-- "이 단계에 설정된 모델"만 안다. 기본값도 Java 상수가 아니라 이 마이그레이션이 심는
-- 행으로 존재한다 — 상수로 두면 설정이 비어 있을 때 조용히 그 값으로 폴백하고,
-- 관리 화면에서 바꾼 값이 실제로 쓰이는지 확인할 수 없다.
--
-- 설정 행이 없으면 호출은 실패해야 한다. 폴백이 없는 것이 의도다.
-- ---------------------------------------------------------------------------
CREATE TABLE ai_stage_settings (
    id         BIGSERIAL    PRIMARY KEY,
    stage      VARCHAR(60)  NOT NULL,
    vendor     VARCHAR(40)  NOT NULL,
    model      VARCHAR(120) NOT NULL,
    updated_by VARCHAR(200),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_ai_stage_settings_stage UNIQUE (stage)
);

-- 벤더별 API 키 --------------------------------------------------------------
-- 키는 암호문으로만 들어간다. 평문으로 두면 DB 백업·덤프·읽기 권한이 곧 키 유출이 되고,
-- 유출을 알아챌 방법도 없다. 마스터 키는 애플리케이션 설정(환경변수)에서 오고, DB 에는
-- 절대 들어가지 않는다 — 둘이 같은 곳에 있으면 암호화가 의미를 잃는다.
--
-- key_hint 는 화면 표시용 뒤 4자다. 운영자가 "지금 어느 키가 들어 있는지"를 확인할 수
-- 있어야 교체 작업이 성립하는데, 그걸 위해 복호화를 노출할 수는 없다.
CREATE TABLE ai_credentials (
    id             BIGSERIAL   PRIMARY KEY,
    vendor         VARCHAR(40) NOT NULL,
    api_key_cipher TEXT        NOT NULL,
    key_hint       VARCHAR(40) NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    CONSTRAINT uq_ai_credentials_vendor UNIQUE (vendor)
);

-- 기본값 --------------------------------------------------------------------
-- 생성·매핑·검증·개인화는 전 단계 Claude Opus 5 다.
--
-- 임베딩만 다른 벤더인 이유는 Anthropic 이 임베딩 API 를 제공하지 않기 때문이다.
-- "전 단계 Claude" 를 임베딩에도 적으면 설정은 그럴듯한데 호출이 실패한다.
INSERT INTO ai_stage_settings (stage, vendor, model, created_at, updated_at) VALUES
    ('SKELETON_BODY',   'ANTHROPIC', 'claude-opus-5',   NOW(), NOW()),
    ('TOPIC_MAPPING',   'ANTHROPIC', 'claude-opus-5',   NOW(), NOW()),
    ('VERIFICATION',    'ANTHROPIC', 'claude-opus-5',   NOW(), NOW()),
    ('PERSONALIZATION', 'ANTHROPIC', 'claude-opus-5',   NOW(), NOW()),
    ('EMBEDDING',       'VOYAGE',    'voyage-3-large',  NOW(), NOW());
