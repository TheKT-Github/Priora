# Priora — Intelligent Task Prioritization & Workload Management Platform

> **A self-prioritizing task board powered by real-time mathematical scoring.**  
> Stop guessing what is urgent. Let the Dynamic Gravity Engine decide.

---

## 1. Project Overview

Traditional project management tools like Jira or Trello rely on manually assigned priority tags (*Low, Medium, High, Critical*). In practice, priority tags suffer from label inflation, decay over time, and fail to reflect dynamic delivery risks such as cascade dependencies, approaching deadlines, or team workload bottlenecks.

**Priora replaces static labels with a real-time mathematical score** called the **Gravity Score**. Every task is evaluated dynamically against six urgency factors — downstream blast radius, approaching/overdue deadlines, stagnation aging, assignee workload density, target deployment environment, and blocker status — and automatically categorized into one of three visual priority zones on a dynamic canvas.

Engineers and engineering managers always know what to work on next without manual triage meetings.

---

## 2. Technology Stack & Architecture

### Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.2.3
- **Security**: Spring Security 6 (HTTP Basic Authentication with BCrypt password hashing)
- **Data & Persistence**: Spring Data JPA, Hibernate ORM
- **Database Support**:
  - H2 Database (in-memory for zero-setup local development and unit/integration testing)
  - PostgreSQL & Oracle JDBC drivers (production relational databases)
- **Validation**: Jakarta Bean Validation (`spring-boot-starter-validation` / Hibernate Validator)
- **Documentation**: SpringDoc OpenAPI / Swagger UI 2.3.0 (`/swagger-ui/index.html`)
- **Testing**: JUnit 5, Spring Boot Test (`MockMvc`), Hamcrest

### Frontend
- **Framework**: Angular 16 (Standalone Components, Signals & Computed Reactivity)
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **Routing**: Angular Router with functional Route Guards (`authGuard`, `managerGuard`, `adminGuard`)
- **State Management**: Angular 16 Signals with `sessionStorage` token isolation

---

## 3. Prerequisites

To run Priora locally, ensure the following are installed:
- **Java Development Kit (JDK)**: Java 17 or higher (`java -version`)
- **Node.js**: v18.x or v20.x (`node -v`)
- **npm**: v9.x or higher (`npm -v`)
- **Maven**: 3.9.x (or use the provided Maven wrapper `mvnw` / `mvn.cmd`)

---

## 4. Getting Started & Startup Instructions

### Backend Startup

The backend defaults to the `dev` profile, running an in-memory H2 database with automated Flyway migrations and pre-seeded demonstration data:

```powershell
cd backend
# Run with default dev profile on port 8081 using the Maven Wrapper
./mvnw spring-boot:run        # Linux / macOS
.\mvnw.cmd spring-boot:run    # Windows
```

Once started, the backend is accessible at:
- **REST API**: `http://localhost:8081/api`
- **Swagger UI**: `http://localhost:8081/swagger-ui/index.html`
- **OpenAPI v3 Specs**: `http://localhost:8081/v3/api-docs`
- **H2 Web Console**: `http://localhost:8081/h2-console` (JDBC URL: `jdbc:h2:mem:prioradb`, User: `sa`, Password: empty)

### Built-in Demonstration Accounts

When started in `dev` mode, Priora automatically seeds sample users and a realistic 6-task diamond DAG. All demo accounts use the password: **`PrioraDemo2026!`**

| Username | Role | Full Name | Department | Permissions |
|---|---|---|---|---|
| `project.manager` | `ROLE_PROJECT_MANAGER` | Marcus Vance | Product & Engineering | Full access: approvals, settings, user creation, canvas |
| `team.lead` | `ROLE_TEAM_LEAD` | Elena Rostova | Platform Engineering | Task creation, approval queue, canvas, reassignment |
| `developer.one` | `ROLE_TEAM_MEMBER` | David Chen | Core Backend | Task status progression, comments, personal views |
| `developer.two` | `ROLE_TEAM_MEMBER` | Sarah Jenkins | Frontend Architecture | Task status progression, comments, personal views |

### Frontend Startup

```powershell
cd frontend
# Install dependencies
npm install

# Start development server
npm start
```

The frontend application opens at `http://localhost:4200`.

---

## 5. Environment Profiles & Configuration

Priora supports three distinct runtime configuration profiles:

| Profile | Target Database | Schema Management | Intended Use |
|---|---|---|---|
| `dev` *(default)* | H2 In-Memory (PostgreSQL mode) | Flyway Migrations (V1, V2, V3) + `validate` | Local development with zero setup. H2 console & demo data seeder enabled. |
| `test` | H2 In-Memory (isolated schema) | Flyway Migrations (V1, V2, V3) + `validate` | Fast automated test suite execution with demo data strictly disabled. |
| `prod` | PostgreSQL (External Server) | Flyway Migrations (V1, V2, V3) + `validate` | Production deployment. Fails fast if env vars are absent. |

### Required Production Environment Variables

In the `prod` profile, Priora **fails fast** on startup if any of the following environment variables are missing. Silent fallback to local or in-memory credentials is strictly prohibited:

| Variable | Description | Example Format |
|---|---|---|
| `DB_URL` | JDBC connection URL for PostgreSQL | `jdbc:postgresql://postgres-host.internal:5432/priora_db` |
| `DB_USERNAME` | Database username | `priora_app_user` |
| `DB_PASSWORD` | Database password | *(Secret value)* |

To run the application with the production profile:
```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/priora"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_secure_password"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```

> **Database Schema Setup**: Flyway manages database migrations (`V1__initial_schema.sql`, `V2__phase8_performance_indexes.sql`, `V3__phase10_pagination_indexes.sql`) across all environments, with Hibernate enforcing strict `ddl-auto=validate`.

---

## 6. The Gravity Engine Scoring Formula

Every task receives a dynamic **Total Gravity Score** computed on each API call:

$$\text{Total Gravity Score} = \text{Base Score} \times \text{Workload Multiplier} \times \text{Environment Multiplier}$$

$$\text{Base Score} = \text{Blast Radius} + \text{Deadline Urgency} + \text{Stagnation} + \text{Blocker Penalty} + \text{Custom Factor}$$

### Formula Components

| Factor | Calculation Formula | Range / Default |
|---|---|---|
| **Blast Radius** | $(15.0 \times \text{direct}) + (5.0 \times \log_2(1 + \text{indirect}))$ | 0.0 pts if `enableDependencyScore = false` |
| **Deadline Urgency (Approaching)** | $\frac{100.0}{1.0 + e^{0.1 \times (T_{\text{remaining}} - 24.0)}}$ (sigmoid curve) | 0.0 to 100.0 pts (50 pts at exactly 24h remaining) |
| **Deadline Urgency (Overdue)** | $100.0 + \min(30.0, \text{hours overdue})$ | 100.0 to 130.0 pts max |
| **Stagnation Aging** | $\min(15.0, \frac{\text{days inactive}}{7.0} \times 5.0)$ *(paused during `IN_REVIEW` and `DONE`)* | 0.0 to 15.0 pts (0.0 if `enableStagnationScore = false`) |
| **Blocker Penalty** | Flat $+25.0\text{ pts}$ if status is `BLOCKED` | **Architectural invariant** (always applied) |
| **Custom Factor** | `settings.customFactorPoints` | Applied **only** when enabled AND `task.isCustomFactorApplied() = true` |
| **Workload Multiplier** | $1.0 + 0.05 \times \max(0, \text{assignee active tasks} - 3)$ | Defaults to 1.0x if `enableWorkloadDensityScore = false` |
| **Environment Multiplier** | `PRODUCTION` = 1.5×, `STAGING` = 1.2×, `DEVELOPMENT` = 1.0× | **Architectural invariant** (always applied) |

Final scores are rounded to 1 decimal place (`Math.round(score * 10.0) / 10.0`).

### The 3 Visual Canvas Priority Zones

Tasks are placed dynamically into three canvas zones based on canonical score cutpoints:

| Zone | Condition | Meaning |
|---|---|---|
| 🔴 **Focus Zone** | Score $\ge 80.0$ | Critical priority. Immediate engineering focus required. |
| 🟡 **Drifting Zone** | $50.0 \le \text{Score} < 80.0$ | Active mid-priority work progressing steadily. |
| 🟢 **Orbit Zone** | Score $< 50.0$ **or** status is `DONE` | Low-urgency backlog, or completed tasks. |

> **Zone Override**: A status of `DONE` unconditionally overrides the zone to `ORBIT` regardless of calculated score.

---

## 7. Directed Acyclic Graph (DAG) & Cycle Prevention

Priora models task dependencies as a **Directed Acyclic Graph (DAG)**:
- **Edge Direction**: `PARENT_TASK_ID` $\rightarrow$ `DEPENDENT_TASK_ID` in table `TASK_DEPENDENCIES`.
- A Parent task must be completed to unblock its Dependent tasks.
- **Cycle Prevention Algorithm**:
  - Before any dependency relationship mutation (`POST /api/tasks`, `PUT /api/tasks/{id}/dependencies`), the service executes an in-memory Breadth-First Search (BFS) reachability check starting from the target task following `dependentTasks` downstream.
  - If any proposed parent is reachable downstream, adding the edge would introduce a directed cycle ($P \rightarrow T \rightarrow \dots \rightarrow P$).
  - The request is immediately rejected with `CircularDependencyException` (HTTP 400 Bad Request) **before** modifying entity collections or issuing database writes.
  - `@Transactional` ensures automatic rollback of JPA session changes upon exception.
  - Self-dependencies ($T \rightarrow T$) and nonexistent parent IDs are validated and rejected.

---

## 8. Role-Based Access Control (RBAC) Matrix

Priora defines four official roles:
1. `ROLE_PROJECT_MANAGER` (Full administration, user provisioning, task creation, settings)
2. `ROLE_TEAM_LEAD` (Task creation, approvals, reassignments, deadline changes, settings)
3. `ROLE_TEAM_MEMBER` (Developer: view tasks, update status, change own password)
4. `ROLE_TESTER` (QA: view tasks, update status, change own password)

### API Authorization Matrix

| Endpoint | Method | Permitted Roles | Notes |
|---|---|---|---|
| `/api/users/register` | `POST` | Public (`permitAll`) | Always assigns `ROLE_TEAM_MEMBER`. Client role input is discarded. |
| `/api/users` | `POST` | `PROJECT_MANAGER` | Creates team accounts with role validated against whitelist. |
| `/api/users` | `GET` | All 4 Roles | Lists team members for assignment. |
| `/api/users/change-password` | `PUT` | All 4 Roles | Authenticated user changes own password. |
| `/api/auth/me` | `GET` | All 4 Roles | Returns current profile and role. |
| `/api/tasks` | `GET` | All 4 Roles | Retrieves approved tasks with gravity scores (array contract). |
| `/api/tasks/search` | `GET` | All 4 Roles | Paginated catalog (`PageResponse<TaskResponseDto>`) with multi-param filtering, sorting allowlist, and 64-bit offset overflow protection. |
| `/api/tasks` | `POST` | `PROJECT_MANAGER`, `TEAM_LEAD` | Creates a task ticket. |
| `/api/tasks/pending` | `GET` | `PROJECT_MANAGER`, `TEAM_LEAD` | View queue of pending developer tickets (array contract). |
| `/api/tasks/pending/search` | `GET` | `PROJECT_MANAGER`, `TEAM_LEAD` | Paginated pending approval queue (`PageResponse<TaskResponseDto>`). |
| `/api/tasks/*/approve` | `PUT` | `PROJECT_MANAGER`, `TEAM_LEAD` | Approves pending developer ticket. |
| `/api/tasks/*/reject` | `PUT` | `PROJECT_MANAGER`, `TEAM_LEAD` | Rejects pending ticket with required reason. |
| `/api/tasks/**` | `DELETE` | `PROJECT_MANAGER`, `TEAM_LEAD` | Deletes task with referential integrity decoupling. |
| `/api/tasks/*/deadline` | `PUT` | `PROJECT_MANAGER`, `TEAM_LEAD` | Modifies task deadline. |
| `/api/tasks/*/reassign` | `PUT` | `PROJECT_MANAGER`, `TEAM_LEAD` | Reassigns task to team member. |
| `/api/tasks/*/dependencies` | `PUT` | `PROJECT_MANAGER`, `TEAM_LEAD` | Replaces parent dependencies (DAG cycle validated). |
| `/api/tasks/*/ping` | `POST` | `PROJECT_MANAGER`, `TEAM_LEAD` | Toggles priority blocker ping alert. |
| `/api/tasks/*/status` | `PUT` | All 4 Roles | Team members update status (`TODO`, `IN_PROGRESS`, `DONE`, etc.). |
| `/api/settings` | `GET` | All 4 Roles | Reads Gravity Engine configuration. |
| `/api/settings` | `PUT` | `PROJECT_MANAGER`, `TEAM_LEAD` | Modifies Gravity Engine toggles and weights. |

---

## 9. Running Tests & Building

### Run Backend Tests

```powershell
cd backend
# Execute the complete automated test suite via the Maven Wrapper
./mvnw test        # Linux / macOS
.\mvnw.cmd test    # Windows
```

Runs the complete **192-test automated suite** across 18 test suites:
- `UserValidationServiceTest` (46 tests — OWASP username, password, full name, employee ID validation)
- `SecurityAndRbacTest` (20 tests — Spring Security filter chain, BCrypt encoding, role permissions)
- `GravityEngineServiceTest` (20 tests — formula components, blast radius, sigmoid deadlines, zones)
- `TaskValidationAndControllerTest` (17 tests — request DTO validation, boundary constraints)
- `FlywayMigrationIntegrationTest` (12 tests — schema migration history, table constraints, composite indexes)
- `TaskDependencyAndCycleTest` (11 tests — DAG BFS cycle prevention, self-dependency rejection)
- `GlobalExceptionHandlerTest` (10 tests — centralized RFC 7807 error status code mappings)
- `Phase10PaginationIntegrationTest` (9 tests — pagination, filtering, allowlisted sorting, 64-bit overflow guards)
- `GravityEngineRegressionTest` (9 tests — fixed-clock golden-value regression tests)
- `TaskDependencyTraversalServiceTest` (6 tests — multi-root BFS traversal, edge caching)
- `Phase8HibernatePerformanceIntegrationTest` (5 tests — Cartesian product prevention, single-valued joins)
- `Phase8TaskServiceTest` (5 tests — task service orchestration and dependency isolation)
- `SwaggerSecurityTest` (5 tests — public vs authenticated endpoint security rules)
- `EndToEndWorkflowIntegrationTest` (5 tests — complete task lifecycle and transactional boundaries)
- `OpenApiDocumentationTest` (5 tests — OpenAPI v3 specification generation)
- `Phase10QueryCountIntegrationTest` (3 tests — bounded SQL query count verification)
- `GravityQueryOptimizationAndConsistencyTest` (3 tests — workload cache consistency)
- `DevDataSeederTest` (1 test — local development demo data seeder idempotency and BCrypt matching)

### Build Frontend

```powershell
cd frontend
npm run build
```

Generates production-optimized Angular AOT bundles in `frontend/dist/`.

---

## 10. Technical Documentation Library

For in-depth architectural and mathematical specifications, refer to the deep-dive documentation library in `docs/`:

- 📐 **[System Architecture (`docs/ARCHITECTURE.md`)](docs/ARCHITECTURE.md)**: Angular 16 Standalone/Signals architecture, Spring Boot multi-tier backend design, and request/response processing lifecycles.
- 🧮 **[Gravity Engine Specification (`docs/GRAVITY_ENGINE.md`)](docs/GRAVITY_ENGINE.md)**: Exact mathematical formulas, sigmoid deadline proximity, logarithmic blast radius, linear stagnation aging, workload multipliers, and canvas zone thresholds.
- 🕸️ **[Dependency Graph & DAG Traversal (`docs/DEPENDENCY_GRAPH.md`)](docs/DEPENDENCY_GRAPH.md)**: Pre-commit BFS cycle prevention, multi-root BFS traversal pipeline, outgoing adjacency caching, and two-stage server-side pagination.
- 🗄️ **[Database Schema & REST API Reference (`docs/DATABASE_AND_API.md`)](docs/DATABASE_AND_API.md)**: Mermaid Entity-Relationship diagram, Flyway migration log, composite B-tree indexing strategy, comprehensive endpoint reference, and RBAC matrix.

---

## 11. Known Architectural Limitations & Future Roadmap

1. **HTTP Basic Token Reversibility in Browser Storage**:
   - The application uses HTTP Basic authentication. To eliminate raw password storage, `auth.service.ts` stores only the Base64 token (`btoa("username:password")`) in `sessionStorage`.
   - `Base64` is an encoding scheme, NOT encryption. It is reversible (`atob()`). Storing it in `sessionStorage` limits its lifetime to the active browser tab, but does not protect against Cross-Site Scripting (XSS).
   - *Future Roadmap*: Transition from HTTP Basic to short-lived JSON Web Tokens (JWT) with HTTP-only, Secure session refresh cookies.
2. **CSRF Protection Disabled**:
   - CSRF is disabled in `SecurityConfig` because the API is stateless and does not rely on browser session cookies. If cookie-based authentication is introduced in the future, CSRF tokens must be synchronized.
3. **Recursive SQL vs In-Memory Graph Traversal**:
   - While Phase 10 bounds SQL query counts via batched frontier lookups, graphs spanning millions of tasks can be offloaded to recursive SQL Common Table Expressions (`WITH RECURSIVE`) directly in the database engine.