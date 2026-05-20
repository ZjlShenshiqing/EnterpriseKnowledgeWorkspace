# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build the entire project (Java 17, Maven)
mvn clean package -DskipTests

# Build a single module
mvn clean package -pl enterprise-knowledge-ai-service -DskipTests

# Run all tests
mvn test

# Run tests in a single module
mvn test -pl enterprise-knowledge-ai-service

# Run a single test class
mvn test -pl enterprise-knowledge-ai-service -Dtest=KbDocumentServiceImplTest

# Run each service locally (Spring Boot 3.3.5):
#   enterprise-gateway-service      → :8086 (Spring Cloud Gateway)
#   enterprise-knowledge-ai-service → :8081
#   enterprise-collaboration-service → :8082
#   enterprise-workbench-service    → :8083
mvn spring-boot:run -pl enterprise-knowledge-ai-service
```

## Architecture

Microservice monorepo — Maven multi-module, `groupId com.zjl`. Four services plus a shared `frameworks/` library. The gateway is the **only entry point** for frontend traffic; individual services can also be hit directly during development.

### frameworks/ — shared library (not a service)

- **`frameworks-common-spring-boot-starter`**: `Result<T>` (`code`, `message`, `data`, `traceId`), `Results` factory, `BizException`, `ErrorCode` enum (5-digit: 40xxx client, 50xxx server, 503xx external), `PageResult`, `TraceIdHolder`.
- **`frameworks-web-spring-boot-starter`**: `GlobalExceptionHandler` (maps `BizException` → `Result`, validation exceptions → PARAM_INVALID, unhandled → SYSTEM_ERROR), `TraceIdFilter`.
- Business services depend on `frameworks-web-spring-boot-starter` (transitively pulls in common). **Never copy shared code into service modules.**

### enterprise-gateway-service (`:8086`)

Spring Cloud Gateway + WebFlux + Spring Security. Handles auth, JWT, RBAC, user/dept/role management, IP blacklist/whitelist, per-IP rate limiting. Routes to downstream services. **Uses JPA/Hibernate** for its own tables (`enterprise_gateway` database). JWT issued here; downstream services receive identity via headers (`X-User-Id`, `X-Department-Id`, `X-Project-Id`, `X-Is-Admin`).

Key files: `JwtAuthenticationWebFilter.java`, `AuthController.java`, `SystemAdminController.java`

### enterprise-knowledge-ai-service (`:8081`)

The most developed service. Knowledge base + document ingestion + chunking + Milvus vector storage. **Uses MyBatis-Plus**, not JPA. Key capability areas:

- **Knowledge bases** (`kb_knowledge_base`): logical groupings, each bound to a distinct Milvus collection and optional embedding model.
- **Document upload** → `PENDING` → `start-chunk` → `RUNNING` (async) → `SUCCESS` / `FAILED`. Uses Apache Tika for parsing, strategy-based chunking (FIXED_SIZE / PARAGRAPH), and `@TransactionalEventListener` + `@Async` for async chunk execution.
- **Chunks + vectors**: `kb_document_chunk` rows with Milvus vectors (schema: `id` / `content` / `metadata` JSON / `embedding` FloatVector). Vector write is optional — controlled by `app.knowledge.vector-write-enabled` and `app.knowledge.embedding-model`.
- **Permission model**: `permission_type` on document (ALL / DEPARTMENT / PROJECT / USER / ADMIN) + `kb_document_permission` rows. Permission SQL in `KbDocumentMapper.xml`.

Key files: `KbDocumentServiceImpl.java` (core logic), `MilvusVectorWriter.java`, `DocumentChunkEventListener.java`, `KbMilvusRoutingService.java` (routes document → Milvus collection + embedding model)

### enterprise-collaboration-service (`:8082`), enterprise-workbench-service (`:8083`)

Skeleton services — each contains only a `Main.java`. Not yet built out.

## Key Patterns

### Response flow
- Controllers return `Result<T>` via `Results.success(data)` or `Results.success()`.
- Errors: throw `new BizException(ErrorCode.XXX)` or `new BizException(ErrorCode.XXX, "message")`. The `GlobalExceptionHandler` converts to `Result`.
- All responses include `traceId` from MDC (set by `TraceIdFilter`).
- Pagination: `PageResult.of(current, size, total, records)` wrapped in `Results.success(...)`.

### User identity in downstream services
`UserContextInterceptor` parses headers → `UserContext` (record with `userId`, `departmentId`, `projectId`, `isAdmin`). Controllers get it via `UserContextHolder.get()`. Permission checks use this context.

### MyBatis-Plus conventions
- Entity classes in `entity/` package. Mapper interfaces in `mapper/` extend `BaseMapper<T>`. Custom SQL in `src/main/resources/mapper/*.xml`.
- Services extend `ServiceImpl<Mapper, Entity>` — use `baseMapper` for DB access.
- Logical delete: `deleted` field (1=deleted, 0=active), configured globally.
- ID generation: `IdWorker.getId()` (snowflake) for manual cases.
- Pagination: `Page<T>(current, size)` + mapper's `selectPage`.
- Updates: use `Wrappers.lambdaUpdate(T.class)` or `Wrappers.lambdaQuery(T.class)` for type-safe conditions.

### Validation
Jakarta Validation on request DTOs (`@Valid` on controller params). Validation failures caught by `GlobalExceptionHandler` → `ErrorCode.PARAM_INVALID`.

## Important Conventions

- **Javadoc only**: All class/method/field comments must use `/** ... */` block format (per `constitution.md`). No `//` or `/* */`.
- **`docs/AGENTS.md`** is the authoritative rules file — read it for coding rules, security constraints, status enums, database conventions, and prohibited practices.
- **Status enums**: `DocumentStatus` governs the document lifecycle. Primary path: `PENDING → RUNNING → SUCCESS` / `FAILED`. Enum includes reserved values (DRAFT, PUBLISHED, etc.) for future use.
- **No physical deletes** for key business data — use `deleted` flag.
- **Path-based security**: gateway whitelist controls which endpoints skip auth. New public endpoints must be added to `app.security.whitelist.paths` in gateway config.

## Configuration

Key `application.yml` properties (knowledge-ai-service):

| Property | Purpose |
|---|---|
| `app.kb.upload-dir` | Local file storage directory (default `./data/kb-uploads`) |
| `app.knowledge.embedding-model` | Embedding model name; empty = skip vectorization |
| `app.knowledge.vector-write-enabled` | Global vector write switch |
| `app.milvus.uri` | Milvus gRPC endpoint |
| `app.milvus.collection` | Default Milvus collection name |
| `app.milvus.vector-dimension` | Must match embedding model output |
| `app.milvus.fail-on-init` | If true, startup fails when Milvus is unreachable |

Database: MySQL. Knowledge-ai uses `enterprise_knowledge_ai` schema (auto-init via `schema.sql` when `spring.sql.init.mode=always`). Gateway uses `enterprise_gateway` schema (manual SQL, `ddl-auto: none`).

## Core Documentation (docs/)

- `AGENTS.md` — coding rules, security, state enums, forbidden practices
- `constitution.md` — project charter, non-negotiable rules
- `development-flow.md` — phased development plan with step definitions
- `step3-summary.md` — detailed implementation overview of the knowledge base closed loop (status machine, Milvus schema, API inventory, module index)
- `api.md` — full API specification
- `database.md` — ER diagrams and table definitions
- `sdd.md` — system architecture diagrams
- `SKILL.md` — capability boundaries and tech stack
- `deployment.md` — environments, monitoring, backup strategy
