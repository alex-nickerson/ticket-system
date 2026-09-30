# ticket-system WIP

A small ServiceNow-style IT service desk: Spring Boot REST API, Angular frontend,
PostgreSQL, deployed to Azure.

Status: **milestone 1 — tickets core.** The ticket API (create, list with filters,
fetch, amend) is live and testable through Swagger UI. There is no authentication
yet and no user interface beyond the Angular skeleton.

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
cd backend && ./mvnw spring-boot:run

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

### Acting as a user

Authentication arrives in milestone 2. Until then every request that needs to know
who is asking takes an `X-Acting-User` header holding one of these seeded user ids:

| Id | Name | Role |
|---|---|---|
| 1 | Dev Requester | `REQUESTER` |
| 2 | Dev Agent | `AGENT` |
| 3 | Dev Admin | `ADMIN` |

```bash
curl -s localhost:8080/api/tickets -H 'X-Acting-User: 2'

curl -s -X POST localhost:8080/api/tickets   -H 'X-Acting-User: 1' -H 'Content-Type: application/json'   -d '{"type":"INCIDENT","title":"Laptop will not power on",
       "description":"No lights, no fan.","categoryId":1,"priority":"P1"}'
```

The role is not enforced anywhere yet — the header only identifies the requester
and answers `?mine=true`.

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
