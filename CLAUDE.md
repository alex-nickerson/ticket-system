# Help Desk Ticketing System — Project Spec

> Working name: **ticket-system** This file is the source of truth for scope, architecture, and build order. Claude Code: read this at the start of every session and keep it updated as decisions change.

## Purpose

A portfolio project: a small ServiceNow-style IT service desk. It has two jobs:

1. **Developer roles:** show Java/Spring Boot REST APIs, Angular/TypeScript, automated testing (JUnit), secure coding, CI/CD, and cloud deployment on Azure.
2. **IT / service desk roles:** show a working understanding of ticketing, SLAs, incident triage, and the Joiner/Mover/Leaver (JML) account lifecycle.

**The developer (Alex) is learning this stack.** When introducing a pattern, library, or design decision, explain *why* briefly, in plain language. Prefer clear, conventional code over clever code. Alex needs to be able to defend every design choice in an interview.

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java (current LTS), Spring Boot (latest stable), Maven |
| Persistence | Spring Data JPA, PostgreSQL, Flyway migrations |
| Security | Spring Security; OAuth2 resource server (JWT) |
| API docs | springdoc-openapi (Swagger UI) |
| Frontend | Angular (latest stable), TypeScript, Angular Material, a chart library (e.g. ng2-charts) |
| Auth (frontend) | MSAL Angular (Microsoft Entra ID) |
| Testing | JUnit 5, Mockito, Spring MockMvc, Testcontainers (Postgres); Angular CLI default test runner; Playwright for a few end-to-end flows later |
| Local dev | Docker Compose (Postgres + Azurite for Blob Storage emulation) |
| CI/CD | GitHub Actions |
| Cloud | Microsoft Azure (see below) |

Check start.spring.io and the Angular docs for current versions at setup time; don't pin versions from memory.

**Known-good combination, verified end to end on 2026-09-29.** Look up current versions first as above, but this combination is proven rather than remembered — fall back to it if something does not work:

| Component | Version | Note |
|---|---|---|
| Java | 21 (LTS) | Boot 4's baseline is 17, so 21 is comfortably supported |
| Spring Boot | 4.1.1 | |
| springdoc-openapi | 3.1.1 | The 3.x line targets Boot 4; 2.x targets Boot 3 |
| PostgreSQL | 17 | Matches Azure Flexible Server |
| Node.js | 24.19.0 | |
| Angular, CLI, Material | 22.2.0 | |
| TypeScript | ~6.0.2 | Dictated by Angular, see below |

**TypeScript is the exception to "use the latest":** Angular supports only a narrow TypeScript range (Angular 22 requires `>=6.0 <6.1`), so TypeScript is upgraded when Angular is upgraded, never independently. Configure Dependabot to ignore its major and minor updates in the very first commit — left unconfigured, Dependabot will open a TypeScript major-bump PR within minutes of the repo going up, and merging it makes the frontend unbuildable.

### Why Azure

Entra ID is the identity system most corporate IT runs on, and Alex has admin experience with it (M365, Entra ID, Exchange Online). Signing into the app with Entra ID and mapping Entra **app roles** to app permissions ties the developer story and the IT story together.

### Azure services

| Service | Use |
|---|---|
| Microsoft Entra ID | Sign-in (OIDC); app roles `Requester`, `Agent`, `Admin` |
| Azure App Service (Linux, Java) | Hosts the Spring Boot API |
| Azure Static Web Apps | Hosts the Angular build |
| Azure Database for PostgreSQL – Flexible Server | Production database (smallest Burstable tier) |
| Azure Blob Storage | Ticket attachments (private container, short-lived SAS URLs for download) |
| Azure Key Vault | Secrets (DB password, storage keys); accessed via managed identity |
| Azure Storage Queue + Azure Functions (Java) | Notification pipeline: API enqueues events (SLA breach, ticket assigned), Function sends email |
| Azure Communication Services (Email) | Outbound notification email |
| Application Insights | Logs, request tracing, basic monitoring |

**Cost control (do this before creating anything):** set an Azure budget with email alerts. Use the smallest tiers. Stop the Postgres server when not in use. If Alex is eligible, use Azure for Students credit. Prefer building and testing locally (Docker Compose + Azurite) and deploying only at milestone boundaries.

## Repository layout

```
/backend        Spring Boot API
/frontend       Angular app
/functions      Azure Function(s) for notifications
/infra          Deployment notes/scripts (optionally Bicep later)
/docs           Architecture diagram, API notes, screenshots, decision log
docker-compose.yml
CLAUDE.md
README.md
```

## Roles and permissions

| Role | Can do |
|---|---|
| **Requester** | Create tickets; view and comment on *their own* tickets; upload attachments to their own tickets |
| **Agent** | Everything a requester can, on *all* tickets; assign/reassign; change status and priority; add internal notes (hidden from requesters); work JML checklists |
| **Admin** | Everything an agent can; manage categories and SLA policies; manage users/roles (in dev profile); view all dashboards |

Authorization is enforced **on the server** for every endpoint and every object (a requester requesting someone else's ticket ID gets 404, not the ticket). The frontend hides controls for convenience only.

## Domain model

- **User**: id, entraObjectId (nullable in dev), displayName, email, role, active.
- **Category**: id, name (e.g. Hardware, Software, Access, Network, Onboarding), active.
- **SlaPolicy**: id, priority, firstResponseMinutes, resolutionMinutes, businessHoursOnly (bool; v1 can use 24/7 clock).
- **Ticket**: id, number (human-readable, e.g. `INC-000123` / `REQ-000045`), type (INCIDENT, SERVICE_REQUEST), title, description, category, priority (P1–P4), status, requester, assignee (nullable), createdAt, firstRespondedAt, resolvedAt, closedAt, slaResponseDueAt, slaResolutionDueAt, slaState (ON_TRACK, AT_RISK, BREACHED), slaPausedAt, totalPausedMinutes, version (optimistic locking).
- **Comment**: id, ticket, author, body, internal (bool), createdAt.
- **Attachment**: id, ticket, uploadedBy, filename, contentType, sizeBytes, blobKey, createdAt.
- **TicketEvent** (audit history, append-only): id, ticket, actor, eventType (CREATED, STATUS_CHANGED, ASSIGNED, PRIORITY_CHANGED, COMMENTED, SLA_BREACHED, …), oldValue, newValue, createdAt.
- **JmlRequest**: id, ticket (a SERVICE_REQUEST), kind (JOINER, MOVER, LEAVER), employeeName, employeeEmail, department, effectiveDate.
- **ChecklistItem**: id, jmlRequest, description, done, doneBy, doneAt, sortOrder.

### Ticket lifecycle

```
NEW → ASSIGNED → IN_PROGRESS ⇄ ON_HOLD
                     ↓
                 RESOLVED → CLOSED
                     ↓
                 (REOPENED → IN_PROGRESS)
```

- Invalid transitions are rejected with 409 and a clear message. Keep transition rules in one place (a state-machine class), fully unit tested.
- `firstRespondedAt` is set on the first agent action visible to the requester (public comment or status change).
- RESOLVED auto-closes after N days (config) via scheduled job.

### SLA rules

- On create, look up the SlaPolicy for the ticket's priority and set both due dates.
- Priority change recalculates due dates from `createdAt` (document this choice).
- ON_HOLD pauses the resolution clock; resuming pushes the due date out by the paused duration.
- A scheduled job (every minute) marks tickets AT_RISK at ≥75% of elapsed SLA time and BREACHED past due. It records a TicketEvent and enqueues a notification.
- All time logic takes an injected `java.time.Clock` so it's testable. SLA calculations need thorough unit tests (edge cases: pause/resume, priority change, breach exactly at due time).

### Joiner / Mover / Leaver

Creating a JML request creates a SERVICE_REQUEST ticket plus a default checklist, e.g.:
- **Joiner:** create account, assign licenses, add to groups, provision laptop, set up MFA.
- **Mover:** update groups/access for new department, remove old access, update manager.
- **Leaver:** disable sign-in, revoke sessions, convert mailbox/forward mail, recover hardware, remove licenses.

Checklist templates are data (seeded via Flyway), not hard-coded. The ticket can't move to RESOLVED until all checklist items are done.

## REST API (v1)

Base path `/api`. JSON. Errors use RFC 7807 problem details. Lists are paginated (`page`, `size`, `sort`).

```
GET    /api/me
GET    /api/tickets                      filters: status, priority, assignee, category, slaState, mine, q
POST   /api/tickets
GET    /api/tickets/{id}
PATCH  /api/tickets/{id}                 title/description/category/priority (role-dependent)
POST   /api/tickets/{id}/assign          { assigneeId }
POST   /api/tickets/{id}/transition      { toStatus, note? }
GET    /api/tickets/{id}/comments
POST   /api/tickets/{id}/comments        { body, internal }
GET    /api/tickets/{id}/events
POST   /api/tickets/{id}/attachments     multipart
GET    /api/attachments/{id}/download    returns short-lived SAS URL (or redirect)
POST   /api/jml                          { kind, employeeName, employeeEmail, department, effectiveDate }
PATCH  /api/jml/{id}/checklist/{itemId}  { done }
GET    /api/dashboard/summary            open by status/priority, SLA breach rate, avg time to first response / resolution
GET    /api/admin/categories   POST/PATCH ...
GET    /api/admin/sla-policies PATCH ...
GET    /api/users?role=AGENT
```

Keep controllers thin: controller → service → repository. Use request/response DTOs (Java records); never expose JPA entities directly. Validate input with Bean Validation.

## Security standards (OWASP-minded)

- Server-side authorization on every endpoint and object (see Roles).
- Bean Validation on all inputs; length limits on text fields.
- JPA/parameterized queries only; no string-built SQL.
- Frontend renders user text as text (Angular's default escaping); no `innerHTML` with user content.
- Attachments: allowlist content types and extensions, max size, random blob names, private container, SAS URLs that expire in minutes.
- No secrets in the repo. Local secrets in an untracked `.env`; production secrets in Key Vault via managed identity. Add a secret scanner to CI.
- CORS restricted to the frontend origin.
- Dependabot enabled.
- `docs/SECURITY.md` explains each measure and which OWASP Top 10 risk it addresses.

## Auth: two profiles

- **`dev` profile:** local JWTs for seeded test users (one per role) so development works offline without Entra. Document the seeded users in the README.
- **`prod` profile:** Entra ID. Backend validates Entra-issued access tokens (OAuth2 resource server); roles come from Entra app roles in the token. Frontend signs in with MSAL Angular. On first sign-in, create a local User row keyed by `entraObjectId`.

## Testing standards

- Every feature ships with tests in the same milestone.
- Unit tests: services, the ticket state machine, SLA calculation (with a fixed `Clock`).
- Web layer: MockMvc tests covering validation errors and **authorization** (each role hitting endpoints it should and shouldn't reach).
- Integration: Testcontainers Postgres for repositories and a couple of full-flow tests.
- Frontend: component/service tests for the ticket list, ticket detail, and create form.
- E2E (later milestone): Playwright for create ticket → agent assigns → resolves.
- CI must pass before merging.

## Milestone 0 runbook

Milestone 0 has been built once before. It worked, but only after six avoidable
failures — four of them discovered in CI rather than locally. Everything below is a
fix for something that actually went wrong. Follow it literally; it will save an hour.

### Step 1: check the toolchain before generating anything

```bash
java -version    # need JDK 21
node --version   # must satisfy Angular's "engines" field — check it, never assume
docker info      # must print a server version; Docker Desktop has to be RUNNING
git --version
```

Maven is **not** required: Spring Initializr ships the `mvnw` wrapper, which downloads
the right Maven itself.

If Docker or Node is missing or too old, **stop and tell Alex before writing code.**
Both are blocking: Docker for PostgreSQL, Azurite and Testcontainers; Node for
Angular. Specifically:

- **Angular's Node floor moves between minor releases** and `ng new` refuses to run
  below it. Read the CLI's requirement rather than assuming a recent Node is fine.
- **On Windows, Node's winget package id can be misleading** — a 24.x install was
  registered as `OpenJS.NodeJS.22`, so `winget upgrade` on that id *downgrades* to the
  22 line. Install `OpenJS.NodeJS.LTS` explicitly instead.
- **On Windows, WinNAT reserves shifting blocks of TCP ports**, and the block
  frequently covers Azurite's defaults (10000/10001). `docker compose up` then fails
  with *"An attempt was made to access a socket in a way forbidden by its access
  permissions"* — which is not a port conflict: nothing is listening. Check with
  `netsh interface ipv4 show excludedportrange protocol=tcp` and set
  `AZURITE_BLOB_PORT` / `AZURITE_QUEUE_PORT` in `.env` to ports outside every listed
  range. The ranges change after a reboot, so this can appear on a stack that worked
  yesterday.

### Step 2: know that Spring Boot 4 is not Spring Boot 3

Nearly every tutorial and Stack Overflow answer is written for Boot 3. These
differences do not compile:

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| one `spring-boot-starter-test` | one `-test` starter per module (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-validation-test`, `-actuator-test`, `-flyway-test`) |
| `org.testcontainers.containers.PostgreSQLContainer<T>` (generic) | `org.testcontainers.postgresql.PostgreSQLContainer` (**not** generic — no `<>`) |
| `...boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `...boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| Jackson 2: `com.fasterxml.jackson.databind.ObjectMapper` | Jackson 3: `tools.jackson.databind.ObjectMapper` (annotations stay on `com.fasterxml.jackson.annotation`) |

When generated or example code fails to compile, suspect the version gap first. To
find where a class actually lives, search the local Maven repository rather than
guessing:

```bash
# which jar holds a class? (this is how AutoConfigureMockMvc's new home was found)
for j in $(find ~/.m2/repository -name '*.jar' ! -name '*sources*'); do
  unzip -l "$j" | grep -q 'AutoConfigureMockMvc.class' && echo "$j"
done
```

`org.springframework.test.web.servlet.*` (MockMvc itself, request builders, result
matchers) is unchanged.

**Jackson is the nastiest of these, because the wrong import compiles.** Boot 4
auto-configures Jackson 3 (`tools.jackson.core:jackson-databind`), but Jackson 2 is
still on the classpath transitively, so `com.fasterxml.jackson.databind.ObjectMapper`
resolves at compile time and then fails at runtime with
`NoSuchBeanDefinitionException: No qualifying bean of type
'com.fasterxml.jackson.databind.ObjectMapper'`. Databind types come from
`tools.jackson.databind`; annotations are still `com.fasterxml.jackson.annotation`.

**Spring Initializr's version id is not the Maven version.** The API calls the
current release `4.1.1.RELEASE` and writes that into the generated `pom.xml`, but
Maven Central publishes `4.1.1`. Left as generated, the first build fails with
`Non-resolvable parent POM`. Strip the `.RELEASE` suffix.

### Step 3: Angular 22 uses Vitest, not Karma

`ng new` generates the `@angular/build:unit-test` builder, which runs tests with
Vitest in jsdom. There is no `karma.conf.js` and **CI needs no browser installed** —
do not add a ChromeHeadless setup step, whatever the Angular CI examples online show.

### Step 4: Windows-to-Linux-CI traps

Both of these pass locally and fail in CI:

1. **`mvnw` loses its executable bit.** Windows has no Unix execute permission, so Git
   commits `backend/mvnw` as mode `100644` and the Linux runner fails with
   `./mvnw: Permission denied` (exit 126). `chmod +x` in the working tree does nothing.
   Fix it in the index, and verify:

   ```bash
   git update-index --chmod=+x backend/mvnw
   git ls-files -s backend/mvnw     # must show 100755
   ```

   Leave `mvnw.cmd` non-executable; it is a Windows batch file.

2. **Line endings.** With Git's `core.autocrlf=true` the repo stores LF but the working
   tree gets CRLF, so Prettier passes in CI and fails locally on Angular's own
   generated files. Commit a root `.gitattributes` with `* text=auto eol=lf` (and
   `*.cmd`/`*.bat` as `eol=crlf`) *before* running Prettier, then run
   `npx prettier --write` once to normalise.

### Step 5: verify with the commands CI runs, not approximations

```bash
# Backend — check the exit code directly, never through a pipe
cd backend && ./mvnw -B verify > /tmp/mvn.log 2>&1; echo "exit=$?"

# Frontend — npm ci, NOT npm install
cd frontend
rm -rf node_modules && npm ci        # the step most likely to fail in CI
npm run format:check
npx ng test --watch=false
npx ng build
```

`npm install` silently reconciles a lockfile; `npm ci` refuses to, so it is the only
command that catches peer-dependency conflicts. `ng test` and `ng build` passing
proves nothing about whether `npm ci` will.

Also start the app for real and confirm the documented URLs, rather than trusting that
the tests passing means the README is accurate:

```bash
docker compose up -d && docker compose ps       # wait for "healthy"
cd backend && ./mvnw spring-boot:run
curl -s localhost:8080/actuator/health          # expect {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}' -L localhost:8080/swagger-ui.html; echo    # expect 200
docker exec ticket-postgres psql -U ticket -d ticketdb -c 'select * from flyway_schema_history;'
```

### Step 6: push, then let CI prove it

Work on a branch and open a PR; `main` stays green. Two things about GitHub Actions
that caused confusion last time:

- With triggers `push: [main]` and `pull_request: [main]`, **pushing a branch does not
  run CI.** Opening the PR is what starts the first run. Say so explicitly rather than
  telling Alex to "watch CI" after a branch push.
- **Pull-request checks run against the branch merged into `main`.** A broken `main`
  therefore fails every open PR with errors that have nothing to do with their
  contents. If a PR fails on code it never touched, check `main` before debugging the
  branch.

**Never merge a red PR, including Dependabot's.** Dependabot proposes upgrades; it
does not check whether the ecosystem supports them. A red Dependabot PR gets closed.

### Definition of done

- `./mvnw verify` and the four frontend commands above all pass locally, exit codes
  checked directly.
- The app boots against the Compose database; health, Swagger UI and the Flyway
  history all verified by hand.
- `git ls-files -s backend/mvnw` shows `100755`.
- `.gitattributes`, `.gitignore` (ignoring `.env`), `.env.example`, `docker-compose.yml`,
  `.github/workflows/ci.yml`, `.github/dependabot.yml` (with the TypeScript ignore rule)
  all committed.
- `/functions` and `/infra` contain a placeholder `README.md` — **Git does not track
  empty directories**, so without one the repository layout above is incomplete.
- All CI jobs green on the PR: backend, frontend, and a `gitleaks` secret scan.
- README run instructions and `docs/decisions.md` written.

### Scope notes from the previous build

- The first Flyway migration created and seeded `categories` and `sla_policies`. That
  is slightly ahead of milestone 1, but a Flyway setup with no migration in it is not
  actually verified. Keep it.
- springdoc was added in milestone 0 rather than 1, so Swagger UI is live from the
  first endpoint. One dependency line, worth it.
- Config lives in `application.yml` with `ddl-auto: validate`,
  `open-in-view: false`, and `hibernate.jdbc.time_zone: UTC`.
- Pin container image tags (`postgres:17-alpine`, not `postgres:latest`) in both
  Compose and the Testcontainers configuration.
- No Lombok.

## Build order (milestones)

At ~1–3 hrs/day, expect roughly 6–9 weeks. Each milestone ends with passing tests, a commit, and a short note in `docs/decisions.md` on anything worth explaining in an interview.

0. **Setup:** monorepo, Spring Boot skeleton, Angular skeleton, Docker Compose (Postgres + Azurite), Flyway, GitHub Actions running backend and frontend tests, README with run instructions. **Follow the Milestone 0 runbook above** — it lists the specific failures a previous attempt hit.
1. **Tickets core (backend):** entities, migrations, ticket CRUD, numbering, pagination/filtering, DTOs, validation, problem-details errors, Swagger. Tests.
2. **Auth and roles (dev profile):** Spring Security, seeded users, role and object-level authorization. MockMvc auth tests for every endpoint.
3. **Lifecycle and SLA:** state machine, SLA policies, due dates, pause/resume, scheduled AT_RISK/BREACHED job, TicketEvent audit trail. Heavy unit tests.
4. **Angular: first vertical slice:** dev login, ticket list (filters, SLA badge), create form, ticket detail with status/assign actions, role-aware UI.
5. **First Azure deploy:** budget alert, Postgres Flexible Server, App Service, Static Web Apps, Key Vault + managed identity, Application Insights. Deploy from GitHub Actions. *(Deploying early surfaces cloud problems while the app is still small.)*
6. **Comments, history, attachments:** public vs internal comments, activity timeline in the UI, Blob Storage uploads (Azurite locally), SAS downloads.
7. **Entra ID sign-in:** app registration, app roles, `prod` profile, MSAL Angular.
8. **JML workflows:** JML request form, checklist templates, checklist UI, resolve-gating.
9. **Notifications:** Storage Queue events from the API, Java Azure Function that sends email via Azure Communication Services on assignment and SLA breach.
10. **Dashboard:** summary endpoint and charts (open by status/priority, breach rate, avg response/resolution times).
11. **Polish:** Playwright e2e, empty/loading/error states, seed demo data, architecture diagram, screenshots, `SECURITY.md`, final README.

## Out of scope for v1 (possible later)

Business-hours SLA calendars; email-to-ticket ingestion; knowledge base articles; problem/change management; Bicep infrastructure-as-code; real-time updates via WebSockets/SignalR.

## Working conventions (for Claude Code)

- Work one milestone at a time; confirm the plan for a milestone before writing code.
- Small, focused commits with clear messages.
- Ask before adding a dependency not listed here, and note why in `docs/decisions.md`.
- Never commit secrets or `.env` files.
- Explain non-obvious choices to Alex in a sentence or two as you go.

### Verifying work before calling it done

- **Run the exact command CI runs, not something close to it.** `npm ci` and
  `npm install` fail differently; `ng test` passing says nothing about `npm ci`.
- **Never read an exit code through a pipe.** `cmd | tail` reports `tail`'s status, so a
  failed build looks like a pass. Use `set -o pipefail`, or redirect to a file and check
  `$?` on its own. A build was reported as passing this way once when it had not
  compiled at all.
- **Check the output, not just the exit code**, for anything that can fail silently —
  grep the log for `Tests run:` and `BUILD SUCCESS` rather than assuming.
- **State test results literally.** If something failed, say so and show the output. If a
  check was skipped or could not run locally, say which and why.
- Correct a mistaken claim as soon as it is found, in one plain sentence, and carry on.

### Reporting to Alex

- Lead with what Alex has to do; put the reasoning after it. A long explanation before
  a simple instruction ("open this PR") buries the instruction.
- Flag judgment calls that went beyond the literal ask, so Alex can push back.
- When something fails, say whether it was his code, the environment, or a previous
  step — and if a failure is in `main` rather than the branch under review, say that
  first.
