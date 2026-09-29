-- Reference data for the ticketing system.
--
-- Categories and SLA policies are data, not code: an admin can edit them at
-- runtime (milestone 1 onwards), so they are seeded here rather than hard-coded.

CREATE TABLE categories (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name   VARCHAR(80) NOT NULL,
    active BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_categories_name UNIQUE (name)
);

-- One policy per priority. first_response_minutes and resolution_minutes are
-- offsets from the ticket's created_at; business_hours_only is reserved for a
-- later milestone, so v1 runs on a 24/7 clock.
CREATE TABLE sla_policies (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    priority               VARCHAR(2) NOT NULL,
    first_response_minutes INTEGER    NOT NULL,
    resolution_minutes     INTEGER    NOT NULL,
    business_hours_only    BOOLEAN    NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_sla_policies_priority UNIQUE (priority),
    CONSTRAINT ck_sla_policies_priority CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_sla_policies_first_response_positive CHECK (first_response_minutes > 0),
    CONSTRAINT ck_sla_policies_resolution_positive CHECK (resolution_minutes > 0),
    -- A resolution target earlier than the first-response target would be
    -- unsatisfiable, so reject it at the database level.
    CONSTRAINT ck_sla_policies_resolution_after_response CHECK (resolution_minutes >= first_response_minutes)
);

INSERT INTO categories (name) VALUES
    ('Hardware'),
    ('Software'),
    ('Access'),
    ('Network'),
    ('Onboarding');

INSERT INTO sla_policies (priority, first_response_minutes, resolution_minutes, business_hours_only) VALUES
    ('P1',  15,   240, FALSE),   -- critical:   15 min response,  4 hours to resolve
    ('P2',  30,   480, FALSE),   -- high:       30 min response,  8 hours to resolve
    ('P3',  60,  1440, FALSE),   -- normal:      1 hr  response, 24 hours to resolve
    ('P4', 240,  4320, FALSE);   -- low:         4 hr  response,  3 days to resolve
