# ticket-system WIP

A small ServiceNow-style IT service desk: Spring Boot REST API, Angular frontend,
PostgreSQL, deployed to Azure.

Status: **milestone 2 — auth and roles.** The ticket API is behind bearer-token
authentication with Requester, Agent and Admin roles enforced on the server. Sign-in
through Microsoft Entra ID comes later; for now the dev profile issues local tokens.
There is no user interface yet beyond the Angular skeleton.

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 |
| Node.js | 24 (Angular 22 requires `^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0`) |
| Docker Desktop | running — needed for PostgreSQL, Azurite and Testcontainers |

Maven is **not** required: `backend/mvnw` downloads the correct version itself.

## Running it locally

```bash
# 1. Local configuration (git-ignored; defaults are fine for development)
cp .env.example .env

# 2. PostgreSQL 17 + Azurite (the Azure Blob/Queue Storage emulator)
docker compose up -d
docker compose ps            # wait for ticket-postgres to report "healthy"

# 3. API — http://localhost:8080
#    The dev profile is required: it is what issues local tokens. Without it the
#    API starts but nothing is reachable.
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Frontend — http://localhost:4200
cd frontend && npm install && npm start
```

### URLs

| What | URL |
|---|---|
| API health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI document | http://localhost:8080/v3/api-docs |
| Angular app | http://localhost:4200 |

### Signing in

Every `/api` endpoint needs a bearer token. Under the dev profile you mint one for a
seeded user; there are no passwords, and the endpoint does not exist in any other
profile.

| Id | Name | Role | Can do |
|---|---|---|---|
| 1 | Dev Requester | `REQUESTER` | Raise tickets; see and reword only their own |
| 2 | Dev Agent | `AGENT` | Everything on all tickets; change category and priority |
| 3 | Dev Admin | `ADMIN` | Everything an agent can, plus administration later |

```bash
# Mint a token for the agent
TOKEN=$(curl -s -X POST localhost:8080/api/dev/token   -H 'Content-Type: application/json' -d '{"userId": 2}'   | python -c 'import json,sys; print(json.load(sys.stdin)["token"])')

# Use it
curl -s localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/tickets -H "Authorization: Bearer $TOKEN"
```

In Swagger UI, press **Authorize** and paste the token to try endpoints from the page.

Tokens last an hour. The signing key is a development placeholder in
`application-dev.yml`; override it with `DEV_AUTH_SECRET` if you would rather it were
not in the repository.

What the roles actually mean on the server:

- A requester asking for someone else's ticket gets **404, not 403** — a 403 would
  confirm the ticket exists.
- A requester's ticket list is always scoped to their own tickets, and `?mine=false`
  cannot widen it.
- A requester can change the title and description of their own ticket. Category and
  priority need an agent, so nobody can escalate their own ticket to P1.

### Inspecting the database

```bash
docker exec ticket-postgres psql -U ticket -d ticketdb -c 'select * from flyway_schema_history;'
```

## Running the checks

These are exactly the commands CI runs. Run them before opening a pull request.

```bash
# Backend: compile, unit tests, and integration tests against a real
# PostgreSQL 17 container started by Testcontainers.
cd backend && ./mvnw -B verify

# Frontend
cd frontend
npm ci                       # not `npm install` — see below
npm run format:check
npx ng test --watch=false
npx ng build
```

`npm install` quietly reconciles a lockfile that no longer matches `package.json`;
`npm ci` refuses to, which makes it the only one of the two that reproduces what CI
does. Use it whenever you want to know whether CI will be happy.

## Project layout

```
backend/     Spring Boot API (Java 21, Spring Boot 4, Maven)
frontend/    Angular 22 app (TypeScript, Vitest)
functions/   Azure Functions for notifications  (milestone 9)
infra/       Azure deployment notes             (milestone 5)
docs/        Decision log, architecture notes
```
