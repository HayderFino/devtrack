-- Migracion V3: tareas.
-- Ver docs/architecture.md seccion 4.2 y ADR-0006.

CREATE TABLE tasks (
    id           BIGSERIAL     PRIMARY KEY,
    project_id   BIGINT        NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    description  TEXT,
    status       VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    priority     VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    assignee_id  BIGINT,
    due_date     DATE,
    created_by   BIGINT        NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE INDEX idx_tasks_project_status ON tasks (project_id, status);
CREATE INDEX idx_tasks_project_assignee ON tasks (project_id, assignee_id);
CREATE INDEX idx_tasks_project_due ON tasks (project_id, due_date);

COMMENT ON COLUMN tasks.version IS
    'Locking optimista (@Version). Un conflicto produce 409 OPTIMISTIC_LOCK_CONFLICT.';
COMMENT ON COLUMN tasks.created_by IS
    'Evidencia de auditoria: no se permite cascade, por lo que el usuario no se puede borrar.';
COMMENT ON COLUMN tasks.assignee_id IS
    'ON DELETE SET NULL: borrar un usuario no borra las tareas que tenia asignadas.';
