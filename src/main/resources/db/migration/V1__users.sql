-- Migracion V1: contexto de identidad.
-- Ver docs/architecture.md seccion 4.2 y ADR-0006.
-- Regla: una migracion NUNCA se edita una vez aplicada.

CREATE TABLE users (
    id              BIGSERIAL   PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    platform_role   VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_platform_role CHECK (platform_role IN ('USER', 'ADMIN'))
);

COMMENT ON COLUMN users.email IS 'Normalizado a minusculas por la aplicacion antes de persistir.';
COMMENT ON COLUMN users.password_hash IS 'Hash BCrypt. Nunca se expone en ningun DTO.';
