# HRMS Employee Service — Backend

Spring Boot backend for the HRMS system. Package root: `com.hrms`. Companion Angular frontend lives in a separate workspace (`C:\angular-workspace\projects\ehrms`).

## Stack

- **Java 21**, **Spring Boot 4.0.5** (`spring-boot-starter-parent`)
- **Database**: Oracle 23ai, schema `HRMS`, via `ojdbc11` (JDBC URL `jdbc:oracle:thin:@localhost:1521/ORCLPDB1`)
- **Persistence**: Spring Data JPA / Hibernate, dialect `OracleDialect`. `spring.jpa.hibernate.ddl-auto=validate` — schema/sequences are managed externally; Hibernate never creates or alters tables. Entities must match the existing `HRMS` schema exactly.
- **Security**: Spring Security + JJWT 0.12.6 (`jjwt-api` / `jjwt-impl` / `jjwt-jackson`)
- **Mapping**: MapStruct 1.6.3 + Lombok 1.18.36, wired together via `lombok-mapstruct-binding` 0.2.0 in the `maven-compiler-plugin` annotation-processor config — both annotation processors must stay listed together or MapStruct-generated mappers using Lombok-built objects will silently stop being generated correctly.
- **API docs**: springdoc-openapi (Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`)
- **AI**: `spring-ai` (BOM `spring-ai-bom` 2.0.0-M4) — vector store starter for Oracle, model starter for OpenAI, advisors-vector-store. Currently disabled (`spring.ai.openai.api-key=DISABLED`), wired for future use — don't assume AI endpoints are live.
- **Shared code**: internal dependency `com.hrms:common-lib:1.0.0` (separate Maven module, own repo/folder — not part of this `employee` source tree). Package root `com.hrms.common`, with:
  - `common.audit` — shared auditing support (e.g. created/modified-by/date base entities or listeners)
  - `common.dto` — shared/cross-cutting DTOs used by more than one service module
  - `common.enums` — shared enum types (distinct from a feature module's own `<feature>.enums`, which holds enums local to that feature)
  - `common.exception` — shared exception types / error-handling building blocks
  - `common.security` — shared security utilities (distinct from `auth.security`, which holds this service's own JWT filter/config wiring)
  - `common.util` — general-purpose shared utilities
  Check `common-lib` before writing a new exception type, DTO, audit base class, or utility — it may already exist there, and duplicating it locally instead of extending/importing from `common-lib` creates drift across services.
- **Dev tools**: `spring-boot-devtools` (hot reload, optional/runtime scope), `spring-boot-starter-actuator` (health/info/metrics exposed)
- **Build**: Maven. Lombok is excluded from the fat jar via `spring-boot-maven-plugin` `<excludes>`.

## Module layout

Feature-based packages under `com.hrms`, each following the same internal shape:

```
com.hrms.<feature>.controller
com.hrms.<feature>.dto.request
com.hrms.<feature>.dto.response
com.hrms.<feature>.entity
com.hrms.<feature>.mapper
com.hrms.<feature>.repository
com.hrms.<feature>.service
com.hrms.<feature>.service.impl
```

Current feature modules (under `src/main/java/com/hrms`):
- **`attendance`** — adds `enums` and `scheduler` (scheduled jobs) alongside the standard layers
- **`auth`** — adds `security` (JWT filters/config) and `scheduler`
- **`employee`** — the core module; adds `config`, `repository.projection`, and `repository.specification` (for dynamic/filtered queries)
- **`leave`** — leave management; has `controller`, `dto.request`, `dto.response`, `entity`, `repository`, `service`, `service.impl` — no `mapper` package (entity↔DTO conversion is done another way here, not via MapStruct)
- **`payroll`** — payroll processing; same layers as `leave` plus `enums` — also no `mapper` package

When adding a new feature module, mirror the package shape of `employee`/`auth`/`attendance` (including a `mapper` package for MapStruct-based conversion) rather than following `leave`/`payroll`'s no-mapper pattern, unless you're extending one of those two modules specifically — match whichever module you're already working in. Don't collapse `dto.request` / `dto.response` into a single `dto` package, and don't merge `service` / `service.impl` — interface and implementation stay split.

## Conventions

- **DTOs**: request/response DTOs are separate from entities; MapStruct mappers (`<feature>.mapper`) convert between them. Controllers never return entities directly.
- **JSON casing**: `spring.jackson.property-naming-strategy=LOWER_CAMEL_CASE` — the Angular frontend expects camelCase, so DTO field names are already camelCase in Java; no snake_case conversion needed anywhere.
- **Nulls in responses**: `spring.jackson.default-property-inclusion=NON_NULL` — null fields are omitted from JSON. Don't design frontend or client logic that depends on a field being present-but-null; it will simply be absent.
- **Unknown/missing JSON on input**: `fail-on-unknown-properties=false` and `fail-on-empty-beans=false` — deserialization is lenient; don't rely on Jackson to reject unexpected request fields.
- **DDL**: `ddl-auto=validate` — never assume Hibernate will create/alter tables or sequences. Schema changes go through whatever external migration process manages the `HRMS` schema; entity mappings must match it exactly (column names, types, sequence names) or the app fails to start.
- **Repositories**: use Spring Data repositories. For complex/dynamic filtering use the `repository.specification` pattern already established in `employee`, and `repository.projection` for read-only projected queries, rather than writing ad-hoc native queries.
- **Transactions**: HikariCP `auto-commit=false` — any DB access must go through `@Transactional` service methods; code that touches the DB outside a managed transaction won't auto-commit.

## Configuration (`application.properties`)

- Server port: **8082**; app name `employee-service`
- DB: schema `HRMS` on `localhost:1521/ORCLPDB1`. Credentials are in plaintext in `application.properties` for local dev — **never** print, log, or echo the DB password or the `hrms.jwt.secret` value in generated code, scripts, commit messages, or examples, and don't commit real production credentials the same way.
- HikariCP: pool name `HrmsOraclePool`, max 20 / min idle 5, 30s connection timeout, 10min idle timeout, 30min max lifetime, test query `SELECT 1 FROM DUAL`
- JWT: config under `hrms.jwt.*` — access token expires in 24h (`86400000` ms), refresh token in 7 days (`604800000` ms)
- File uploads: `app.upload.dir=uploads/employee-documents`, `app.base-url=http://localhost:8082`, max file size 50MB / max request 55MB — relevant to any employee-document-upload endpoints
- CORS: `hrms.cors.allowed-origins` is present but **commented out**. If frontend (`http://localhost:4200`) calls start failing on CORS, this is the first thing to check/uncomment.
- Actuator: `health`, `info`, `metrics` exposed, health details always shown
- Swagger/OpenAPI: docs at `/v3/api-docs`, UI at `/swagger-ui.html`, "try it out" enabled
- Logging: `com.hrms` and Spring Security at DEBUG; Hibernate SQL logged with formatted output and bind-parameter values at TRACE. Expect verbose dev logs — use the existing logger rather than adding ad-hoc `System.out` debugging.

## Working in this repo

- Main class: Spring Boot app under `com.hrms` (e.g. `EmployeeServiceApplication` or similar) — devtools is on the classpath, so most code changes hot-reload without a full restart.
- When adding a new endpoint, follow the existing controller → service → service.impl → repository chain, with a MapStruct mapper between entity and DTO, matching the pattern already used in `employee` or `auth`.
- Prefer extending an existing feature module's structure over introducing new package conventions.
- Frontend counterpart is the Angular app at `C:\angular-workspace\projects\ehrms` — when an API contract changes here, check whether the Angular service/model layer needs a matching update.
