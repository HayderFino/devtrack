-- Migracion V2: proyectos y membresia.
-- Ver docs/architecture.md seccion 4.2 y ADR-0004 / ADR-0006.

CREATE TABLE projects (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    description  TEXT,
    owner_id     BIGINT       NOT NULL,
    archived     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_projects_owner ON projects (owner_id);

COMMENT ON COLUMN projects.archived IS 'Borrado logico: DELETE archiva, no elimina (ADR-0006).';

CREATE TABLE project_members (
    id          BIGSERIAL    PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_project_members UNIQUE (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_members_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

CREATE INDEX idx_project_members_user ON project_members (user_id);

COMMENT ON TABLE project_members IS
    'Fuente de verdad de la autorizacion. Un proyecto tiene exactamente un miembro OWNER; '
    'esa invariante la garantiza la capa de servicio, no el esquema.';
COMMENT ON COLUMN project_members.created_at IS 'Momento en que el usuario se unio al proyecto.';
