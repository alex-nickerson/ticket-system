-- Users and tickets.
--
-- Users exist now, before authentication (milestone 2), because a ticket's
-- requester and assignee are foreign keys. The rows seeded at the bottom are
-- development identities; milestone 2 attaches real Entra object ids to them.

CREATE TABLE users (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Null until the user has signed in through Entra ID at least once, which
    -- means it is always null in the dev profile.
    entra_object_id  VARCHAR(64),
    display_name     VARCHAR(120) NOT NULL,
    email            VARCHAR(254) NOT NULL,
    role             VARCHAR(16)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_entra_object_id UNIQUE (entra_object_id),
    CONSTRAINT ck_users_role CHECK (role IN ('REQUESTER', 'AGENT', 'ADMIN'))
);

-- Human-readable ticket numbers are allocated from a sequence per ticket type,
-- so incidents and service requests are numbered independently (INC-000001,
-- REQ-000001). A sequence is used rather than MAX(number) + 1 because it stays
-- correct under concurrent inserts without locking the table.
CREATE SEQUENCE ticket_number_incident_seq AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE ticket_number_service_request_seq AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE tickets (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number                VARCHAR(20)   NOT NULL,
    type                  VARCHAR(20)   NOT NULL,
    title                 VARCHAR(200)  NOT NULL,
    description           VARCHAR(10000) NOT NULL,
    category_id           BIGINT        NOT NULL,
    priority              VARCHAR(2)    NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    requester_id          BIGINT        NOT NULL,
    assignee_id           BIGINT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Set by the lifecycle and SLA work in milestone 3; unused until then.
    first_responded_at    TIMESTAMP WITH TIME ZONE,
    resolved_at           TIMESTAMP WITH TIME ZONE,
    closed_at             TIMESTAMP WITH TIME ZONE,
    sla_response_due_at   TIMESTAMP WITH TIME ZONE,
    sla_resolution_due_at TIMESTAMP WITH TIME ZONE,
    sla_state             VARCHAR(16),
    sla_paused_at         TIMESTAMP WITH TIME ZONE,
    total_paused_minutes  INTEGER       NOT NULL DEFAULT 0,

    -- Optimistic locking: two agents editing the same ticket must not silently
    -- overwrite each other.
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_tickets_number UNIQUE (number),
    CONSTRAINT fk_tickets_category  FOREIGN KEY (category_id)  REFERENCES categories (id),
    CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_tickets_assignee  FOREIGN KEY (assignee_id)  REFERENCES users (id),
    CONSTRAINT ck_tickets_type CHECK (type IN ('INCIDENT', 'SERVICE_REQUEST')),
    CONSTRAINT ck_tickets_priority CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_tickets_status CHECK (status IN (
        'NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'REOPENED')),
    CONSTRAINT ck_tickets_sla_state CHECK (sla_state IS NULL OR sla_state IN (
        'ON_TRACK', 'AT_RISK', 'BREACHED')),
    CONSTRAINT ck_tickets_total_paused_minutes CHECK (total_paused_minutes >= 0)
);

-- Indexes for the list filters in GET /api/tickets. created_at is indexed
-- descending because the default sort is newest first.
CREATE INDEX idx_tickets_status       ON tickets (status);
CREATE INDEX idx_tickets_priority     ON tickets (priority);
CREATE INDEX idx_tickets_requester_id ON tickets (requester_id);
CREATE INDEX idx_tickets_assignee_id  ON tickets (assignee_id);
CREATE INDEX idx_tickets_category_id  ON tickets (category_id);
CREATE INDEX idx_tickets_sla_state    ON tickets (sla_state);
CREATE INDEX idx_tickets_created_at   ON tickets (created_at DESC);

-- Development identities, one per role, so the API can be exercised before
-- Entra ID sign-in exists. Documented in the README.
INSERT INTO users (display_name, email, role) VALUES
    ('Dev Requester', 'requester@example.test', 'REQUESTER'),
    ('Dev Agent',     'agent@example.test',     'AGENT'),
    ('Dev Admin',     'admin@example.test',     'ADMIN');
