# Architecture — machine-sentinel v0.1

## Role

Compose existing machine SoTs into a single observe/classify/ledger heartbeat.

```
MyAgent registries / scripts / logs
        │ read-only
        ▼
 Spring Boot API :3350 ──► app_machine_sentinel.dev.sentinel_event
        │
        └── (later) thin UI :3351 · Agent Portal LLM analyst via Machine Gateway
```

## Stack

- Java 21 + Spring Boot 3.3.5 + JPA + Flyway + Actuator health
- Hikari max-pool 5 / min-idle 1 (shared PG INC-04 pattern)
- No Spring Security in v0.1 (localhost observe); CSS JWKS when public

## Non-duplication

| Concern | SoT (reuse) | Sentinel owns |
|---------|-------------|-----------------|
| Ports | `workflow/ports/` | live probes + ledger |
| DB map | `workflow/db/` | own schema + events |
| PG pressure | `check-pg-connections.ps1` + log | wrap + ledger |
| Auth | CSS | clientId planned |
| Activity narrative | ACTIVITY-LOG | structured events |
| Crew/LLM UX | Agent Portal | hire later |

## MVP order (Grok)

1. Reserve + provision (done)
2. Inventory + probes + PG wrap (this scaffold)
3. Abandon classifier observe-only (this scaffold)
4. Backup freshness (minimal H:/releases stat)
5. Thin UI / LLM analyst later
