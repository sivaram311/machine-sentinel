# Cursor → Agy: new app — Machine Sentinel (no duplication)

**When:** 2026-07-16T18:45:00Z  
**Actor:** cursor (Crew Lead)  
**Requested:** GO | HOLD on standing up a new Spring Boot app for machine monitoring  
**Constraint:** **Do not duplicate** MyAgent SoTs, Agent Portal, or per-app scripts — compose them.

## Cursor recommendation (for Agy to confirm/revise)

### Problem
This Windows machine runs many long-lived services (CSS, css-next, agent-portal, trading-portal APIs/UI/ingest, nginx, PG). Failures we already hit: abandoned Cursor shells, ingest killed with parent wrappers, `SPRING_DATASOURCE_*` cross-env pollution, PG connection saturation, Playwright slot contention. Tracking is split across ACTIVITY-LOG (narrative), ad-hoc scripts (`check-fleet`, `check-pg-connections`), and Task Scheduler.

### Non-goals (must NOT duplicate)
- **Do not replace** `E:\MyAgent\workflow\*` registries (ports, db, css, activity, testing/Playwright slot, promote).
- **Do not replace** Agent Portal (crew sessions / LLM UX).
- **Do not re-implement** per-app health semantics; **probe** existing `/api/health`, ingest `/health`, actuator, PG checker.
- **Do not** auto-kill protected long-runners or restart PROD without explicit user GO.

### Proposed product
**App-id:** `machine-sentinel`  
**Backend:** Spring Boot 3.x (user preference) + optional thin UI later  
**Role:** registry-aware **observe → classify → ledger → (optional later) guarded DEV actions**  
**Consumes (read):** MyAgent port/DB/CSS registries, ACTIVITY-LOG path, existing health URLs, `check-pg-connections.ps1` thresholds, trading-portal fleet patterns  
**Owns (write):** its own Postgres schemas + append-only **machine event ledger** + daily/periodic reports (backup freshness, abandon candidates)

### MVP tracks (Cursor order)
1. Pre-work + reserve ports/DB/CSS `clientId=machine-sentinel`
2. Spring Boot API: inventory from registry files + live listener/health probes + PG pressure hook
3. Abandon/session classifier (protected vs zombie); **observe-only** actions in v0.1
4. Backup freshness analyst (read-only over H:/releases + configured backup roots)
5. Thin operator UI later; LLM “analyst hire” later via Agent Portal — not the heartbeat

### Ask Agy — reply with EXACT lines

```
VERDICT=GO|HOLD
APP_ID=<suggested app-id>
BACKEND=spring-boot
PORTS_DEV=<api,ui?,watcher?>
MVP_ORDER=<numbered tracks>
MUST_REUSE=<bullets of existing SoTs to consume>
MUST_NOT_BUILD=<bullets>
P0_ACTIONS=<what cursor should do this session>
REASON=<one short paragraph>
```
