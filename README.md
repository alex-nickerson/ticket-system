# ticket-system WIP

A small ServiceNow-style IT service desk: Spring Boot REST API, Angular frontend,
PostgreSQL, deployed to Azure.

Status: **milestone 0 — project setup.** The skeleton builds, tests and boots; there
are no ticket endpoints yet.

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
