# Decision log

Short notes on choices worth being able to explain in an interview. Newest last.

## Milestone 0 — project setup

### Monorepo rather than separate repositories

`backend/`, `frontend/`, `functions/` and `infra/` live in one repository. A change
that spans the API and the UI is then one commit and one pull request, and CI can
check them together. The cost is a slightly more complex CI workflow (one job per
part, each with its own working directory), which is cheap at this size.

### Spring Boot 4, and why the tutorials do not apply

The project uses Spring Boot 4.1.1 on Java 21. Boot 4 renamed several artifacts, so
most examples written for Boot 3 do not compile:

- `spring-boot-starter-web` became `spring-boot-starter-webmvc`.
- The single `spring-boot-starter-test` was split into one test starter per module
  (`-webmvc-test`, `-data-jpa-test`, `-validation-test`, `-actuator-test`,
  `-flyway-test`), all of which are declared explicitly in `backend/pom.xml`.
- `org.testcontainers.containers.PostgreSQLContainer<T>` moved to
  `org.testcontainers.postgresql.PostgreSQLContainer` and is no longer generic.

When generated or copied code fails to compile, the version gap is the first thing
to suspect.

One related trap: Spring Initializr's API identifies the release as `4.1.1.RELEASE`,
but the version actually published to Maven Central is `4.1.1`. A generated `pom.xml`
carrying the `.RELEASE` suffix cannot resolve its parent.

### springdoc 3.x, not 2.x

springdoc-openapi's 3.x line targets Boot 4; the 2.x line targets Boot 3. Swagger UI
was added now rather than in milestone 1 so that it is live from the very first
endpoint — it is one dependency and it makes the API browsable while it is being
built.

### Flyway owns the schema; Hibernate only validates it

`ddl-auto: validate` means Hibernate checks that the entities match the migrated
tables and fails at startup if they have drifted, instead of silently altering the
database. Migrations are therefore the single source of truth for the schema, which
is what makes the production deployment predictable.

`open-in-view: false` closes the persistence context at the end of the service call
rather than holding it open through view rendering. The default (`true`) hides lazy
loading in the serialization layer, where it turns into unpredictable extra queries.

`hibernate.jdbc.time_zone: UTC` stores and reads every timestamp as UTC regardless
of the server's zone. SLA arithmetic is the whole point of this application, so it
must not depend on where the process happens to run.

### The first migration seeds reference data

`V1__create_categories_and_sla_policies.sql` creates and seeds `categories` and
`sla_policies`. That is slightly ahead of milestone 1's scope, but a Flyway setup
with no migration in it has not actually been tested — the backend's integration
test now proves the migration applies to a real PostgreSQL 17 container.

Categories and SLA policies are data rather than code because an administrator edits
them at runtime.

### Angular 22 tests with Vitest, not Karma

`ng new` generates the `@angular/build:unit-test` builder, which runs tests with
Vitest in jsdom. There is no `karma.conf.js`, and CI needs no browser installed —
the ChromeHeadless setup step that older Angular CI examples show is unnecessary and
would only slow the run down.

### TypeScript is pinned to Angular's supported range

Angular supports a narrow TypeScript range (Angular 22 requires `>=6.0 <6.1`), so
TypeScript is upgraded when Angular is upgraded and never independently.
`.github/dependabot.yml` ignores TypeScript's major and minor updates for exactly
this reason; patch updates are still allowed. Without that rule, Dependabot opens a
TypeScript major-bump pull request within minutes of the repository going up, and
merging it makes the frontend unbuildable.

### Line endings are normalised in the repository, not in each developer's config

A root `.gitattributes` sets `* text=auto eol=lf`, with `*.cmd` and `*.bat` kept as
CRLF. Relying on each developer's `core.autocrlf` instead means Prettier passes on
Linux CI and fails on a Windows checkout, or the reverse. Committing the rule makes
the behaviour a property of the repository.

### `mvnw` needs its executable bit set in the Git index

Windows has no Unix execute permission, so Git records `backend/mvnw` as mode
`100644` and the Linux CI runner fails with `./mvnw: Permission denied`. Running
`chmod +x` in the working tree changes nothing that Git stores; the fix is
`git update-index --chmod=+x backend/mvnw`, verified with
`git ls-files -s backend/mvnw` showing `100755`. `mvnw.cmd` stays non-executable
because it is a Windows batch file.

### CI runs `npm ci`, never `npm install`

`npm install` quietly reconciles a lockfile that no longer agrees with
`package.json`; `npm ci` refuses to and fails instead. It is therefore the only one
of the two that surfaces a peer-dependency conflict, and `ng test` or `ng build`
passing says nothing about whether it will.

### Container image tags are pinned

`postgres:17-alpine` and `azurite:3.37.0` in Compose, and `postgres:17-alpine` in the
Testcontainers configuration. `:latest` means a rebuild months from now silently gets
a different database version than the one the tests were written against. PostgreSQL
17 also matches what Azure Database for PostgreSQL Flexible Server will run.

### Secret scanning runs on every pull request

A pinned `gitleaks` image scans the full history (`fetch-depth: 0`) on each pull
request. Running it in CI rather than as a local hook means it cannot be skipped.

### Deferred deliberately

- **Spring Security** is milestone 2. Adding it now would secure every endpoint by
  default and get in the way of verifying that health and Swagger UI respond.
- **Angular Material** is listed in the stack but belongs with the first vertical
  slice in milestone 4; there is no UI yet for it to style.
- **Lombok** is not used at all. The domain objects are Java records and small
  entities, and Lombok's annotation processing is a debugging cost with little to
  show for it here.

## Milestone 1 — tickets core

### Users exist before authentication does

`users` is created and seeded in this milestone even though sign-in is milestone
2, because a ticket's requester and assignee are foreign keys and there is no
honest way to model a ticket without them. The seeded rows are development
identities, one per role; milestone 2 attaches real `entra_object_id` values to
them rather than replacing them.

### The acting user is a header, isolated behind one interface

There is no authentication yet, so the API has to be told who is acting. It reads
an `X-Acting-User` header holding a user id. That is obviously not security —
anyone can claim to be anyone — and it is temporary.

What makes it acceptable is that nothing outside `HeaderActingUserProvider` knows
about it. Callers depend on the `ActingUserProvider` interface, so milestone 2
replaces one class with one that reads Spring Security's context and no other
file changes. The alternative considered was putting `requesterId` in the request
body, which was rejected because it would have made the temporary arrangement
part of the published API contract and would still not have answered the `mine`
filter.

Note that the requester is deliberately *not* a field on
`CreateTicketRequest`: if a client could name the requester, anyone could file a
ticket as somebody else.

### Ticket numbers come from one sequence per type

`INC-000123` and `REQ-000045` are allocated from two PostgreSQL sequences. The
obvious alternative, `MAX(number) + 1`, is wrong under concurrency: two
simultaneous inserts read the same maximum and produce the same number. A
sequence is atomic and needs no table lock.

Incidents and service requests are numbered independently, so the prefix and the
counter agree with each other.

### Filtering uses Specifications rather than a query per combination

`GET /api/tickets` has seven optional filters, which is well over a hundred
combinations. Writing a `@Query` for each is not viable, so the filters are
composed with JPA Specifications. Everything goes through the Criteria API, which
means every client-supplied value arrives as a bound parameter and none of it is
concatenated into SQL.

Free-text search (`q`) is a case-insensitive `LIKE` across number, title and
description. It is a substring match, not a token match, so "projector blown"
does not find "Projector bulb has blown" — there is a test asserting exactly
that, so the behaviour is documented rather than accidental. PostgreSQL full-text
search is the upgrade path if volume ever justifies it.

### The list endpoint fetches its associations in one query

`TicketRepository.findAll(Specification, Pageable)` is overridden purely to
attach an `@EntityGraph` covering category, requester and assignee. Without it, a
page of 20 tickets issues 61 queries instead of 1 — the N+1 problem. All three
are to-one associations, so fetching them alongside does not interfere with
pagination the way fetching a collection would.

### Lists return our own page shape, not Spring's `Page`

Spring Data's `PageImpl` serializes to JSON, but Spring itself warns that its
shape is an implementation detail and unstable across versions. Returning it
would make the public API contract depend on a library's internals, so
`PageResponse` is declared explicitly and the API owns its own shape.

### Two response DTOs, because lists do not need descriptions

`TicketResponse` is the full ticket; `TicketSummaryResponse` is what appears in a
list and omits the description, which can be 10,000 characters and is never
rendered in a list view. A test asserts the description is absent from list
responses so the distinction cannot quietly erode.

### A deactivated category is a 400, not a 404

An inactive category still exists and is still referenced by historical tickets,
so pretending it is missing would be untrue. It simply cannot be chosen for new
tickets, which makes it a bad request. Unknown ids are also 400 rather than 404
here, because the thing that was not found is a field in the body, not the
resource being addressed.

### PATCH semantics: null means "leave alone"

`UpdateTicketRequest` treats a null field as absent rather than as "set to null",
which is what makes the endpoint a patch. Bean Validation cannot express
"optional, but not blank if supplied" with `@NotBlank` (which rejects absence) or
`@Size` (which accepts a blank string), so two `@AssertTrue` methods on the
record cover it: one requires at least one field, the other rejects blank text in
whichever fields were supplied.

### Spring Boot 4 serializes with Jackson 3

This one cost a full test run. Boot 4.1.1 pulls in `tools.jackson.core:jackson-databind:3.1.5`
and auto-configures **Jackson 3**. Jackson 2 is still on the classpath
transitively (YAML config parsing, and `jackson-annotations` is still the 2.x
artifact), so `com.fasterxml.jackson.databind.ObjectMapper` compiles perfectly
well — and then fails at runtime with `NoSuchBeanDefinitionException`, because
the auto-configured bean is `tools.jackson.databind.ObjectMapper`.

The rule: in Boot 4, Jackson *databind* types come from `tools.jackson.databind`,
while Jackson *annotations* are still `com.fasterxml.jackson.annotation`.

### Optimistic locking from the start

`Ticket.version` is mapped with `@Version`, so a second concurrent write fails
with a 409 rather than silently discarding the first. Two agents working the same
queue is the normal case at a service desk, so this is cheaper to add now than to
retrofit once there is data.
