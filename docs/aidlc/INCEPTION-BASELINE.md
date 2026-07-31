# AI-DLC Inception Baseline - machine-sentinel

**Captured:** 2026-08-01 (as-is snapshot, not a target design)

## Purpose

Machine Sentinel is a registry-aware **observe → classify → ledger** app for this Windows machine. It composes existing MyAgent sources of truth (port/DB registries, PG connection checks, Cursor terminal metadata, backup roots) into a Spring Boot heartbeat and event ledger; it does not replace ports/DB/CSS/activity registries or Agent Portal. Audience is local ops on the shared MyAgent fleet (DEV-first), with an observe-only safety posture (no auto-kill).

## Tech stack

- **Backend:** Java 21, Spring Boot **3.3.5** (Maven parent), artifact `machine-sentinel-backend` **0.1.0-SNAPSHOT** (`backend/pom.xml`).
- **Libraries (via Spring Boot BOM):** spring-boot-starter-web, data-jpa, validation, actuator, security, oauth2-resource-server; Flyway (+ PostgreSQL support); PostgreSQL JDBC; Jackson JSR-310; test scope: spring-boot-starter-test, H2.
- **DB:** PostgreSQL (DEV profile: `app_machine_sentinel`, schema `dev`); Flyway migration `V1__event_ledger.sql`.
- **UI:** Single static page `ui/index.html` (vanilla HTML/CSS/JS); served by `python -m http.server` on `:3351` (`scripts/start-ui.ps1`).
- **Ops scripts:** PowerShell under `scripts/` (start API/UI, soak check, Session-0 task registration).

## Current features (as-built)

**API** (`SentinelApiController`, base `/api`; CSS JWKS Bearer required except public health):

- `GET /api/health` — public; status `UP`, mode `observe_only`
- `GET /actuator/health` — public (actuator)
- `GET /api/inventory` — reads MyAgent port + DB registries (no rewrite)
- `GET /api/probes` — live listener/HTTP health probes of registry DEV fleet ports
- `GET /api/pg/pressure` — latest PG pressure from MyAgent connections log
- `POST /api/pg/pressure/refresh` — runs MyAgent `check-pg-connections.ps1`, ledgers result
- `GET /api/abandon/candidates` — classify Cursor terminal sessions (observe-only); ledgers scan summary
- `GET /api/backups/freshness` — assess pack age under configured roots (default `H:/releases`); may ledger WARN/CRIT
- `GET /api/events` — recent `sentinel_event` ledger rows
- `GET /api/ops/status` — composite ops snapshot (PG, probes ok/down, backup level); reports `auto_kill_enabled: false`

**Scheduled observe** (`ObserveScheduler`, in-process; intervals from `application.properties`):

- PG log snapshot → category `pg_pressure` (default 5 min)
- Fleet probe summary → `fleet_probe` (default 10 min)
- Backup freshness → `backup_freshness` (default 30 min)
- Abandon scan → `abandon_scan` (default 15 min)

**Security:** OAuth2 resource server via CSS JWKS (`http://127.0.0.1:9000/.well-known/jwks.json`); `clientId` / audience `machine-sentinel`; CORS for UI origins on `:3351`; break-glass `sentinel.security.enabled=false`.

**UI** (`http://127.0.0.1:3351/`): CSS login (`POST …:9000/auth/login`), then panels for Health, Ops status, PG pressure, Backup freshness, Abandon candidates, Recent events.

**Persistence:** table `sentinel_event` (category, severity, source, summary, detail_json, `action_taken` default `observe_only`).

**Ops tooling:** `start-dev.ps1` / `start-ui.ps1`; soak helper `check-soak.ps1`; Session-0 Windows tasks via `register-session0-tasks.ps1` + `run-*-session0.ps1`.

## Deploy topology (known facts below - cross-check against what you find in-repo, note any discrepancy explicitly rather than silently picking one)

| Claim to verify | In-repo finding |
|-----------------|-----------------|
| DEV API `:3350`, CSS JWKS-protected, `/api/health` public | **Confirmed** in `README.md`, `docs/OPS.md`, `application.properties` (`server.port=3350`), `SecurityConfig` (public `/api/health`, `/actuator/health`), start scripts. |
| DEV UI `:3351` | **Confirmed** in `README.md`, `docs/OPS.md`, `scripts/start-ui.ps1`, UI CORS defaults, `ui/index.html` API default `http://127.0.0.1:3350/api`. |
| Ports registry text (external MyAgent) | **Not verifiable inside this repo.** Config points at `E:/MyAgent/workflow/ports/registry.json` (outside this tree). No copy of that registry is checked in here; in-repo docs/config alone confirm 3350/3351. |
| Observe-only / never auto-kills | **Confirmed** as designed and coded: `sentinel.actions.auto-kill-enabled=false`; abandon classifier documents “never kill” and only uses `ProcessHandle` to check aliveness; scheduler comment “Never mutates fleet processes”; ledger `action_taken` defaults to `observe_only`; `/api/ops/status` hardcodes `auto_kill_enabled` false. |
| Watcher `:3352` | Documented in `docs/OPS.md` as **reserved**; observe jobs run **in-process** (no separate watcher service in this repo). |
| PREPROD/PROD ports (435x / 535x) | Documented in `docs/OPS.md` topology table only; no matching Spring profiles or start scripts for those envs found in-repo. |

**Discrepancy (historical docs vs as-built):** `agents/pre-work/02-architecture.md` still says “No Spring Security in v0.1” and “thin UI later”; shipped code has CSS JWKS security and a thin UI on `:3351`. Prefer README/OPS + source over that pre-work note for current state.

## Known debt / gaps (as-is, factual)

- **No automated tests shipped:** `backend/src/test/` has only `resources/application-test.properties` (references classpath fixtures `test-ports.json`, `test-db.json`, `empty.log`, `empty.ps1` that are **not present** in the tree). No `*Test.java` (or other) test sources found. Test dependencies exist in `pom.xml` unused by sources.
- **Soak closeout incomplete in-repo:** `docs/SOAK-0.1.md` instructs setting `soak_met=true|false` and recording the result; the file still has no recorded `soak_met` outcome.
- **No TODO/FIXME comments** found in source/docs via search.
- **UI does not surface** `/api/inventory` or `/api/probes` directly (API endpoints exist; console shows health/ops/pg/backup/abandon/events only).
- **External SoT dependency:** runtime correctness depends on MyAgent paths (`E:/MyAgent/...`) and CSS `:9000` outside this repository.

## Sources consulted

- `README.md`
- `docs/OPS.md` (already modified in working tree before this capture; content read as currently on disk)
- `docs/SOAK-0.1.md`
- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-dev.properties`
- `backend/src/main/resources/db/migration/V1__event_ledger.sql`
- `backend/src/test/resources/application-test.properties`
- `backend/src/main/java/com/delena/machinesentinel/MachineSentinelApplication.java`
- `backend/src/main/java/com/delena/machinesentinel/web/SentinelApiController.java`
- `backend/src/main/java/com/delena/machinesentinel/security/SecurityConfig.java`
- `backend/src/main/java/com/delena/machinesentinel/config/SentinelProperties.java`
- `backend/src/main/java/com/delena/machinesentinel/schedule/ObserveScheduler.java`
- `backend/src/main/java/com/delena/machinesentinel/service/AbandonClassifierService.java` (partial)
- `backend/src/main/java/com/delena/machinesentinel/service/RegistryInventoryService.java` (partial)
- `backend/src/main/java/com/delena/machinesentinel/service/HealthProbeService.java` (partial)
- `ui/index.html`
- `scripts/start-dev.ps1`, `scripts/start-ui.ps1`
- `agents/pre-work/02-architecture.md` (cross-check only; noted as stale vs shipped)
- Repo file inventory via directory listing / glob (no external MyAgent ports registry read)
