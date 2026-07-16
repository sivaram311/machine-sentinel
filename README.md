# Machine Sentinel

Registry-aware **observe → classify → ledger** for this Windows machine.  
Composes MyAgent SoTs — does **not** replace ports/DB/CSS/activity registries or Agent Portal.

| | |
|--|--|
| App id | `machine-sentinel` |
| Backend | Spring Boot 3.3 (Java 21) |
| DEV API | `:3350` |
| DB | `app_machine_sentinel` / schema `dev` |
| CSS | `clientId=machine-sentinel` (planned; observe APIs open in v0.1) |
| Mode | **observe-only** — no auto-kill (INC-06) |

## Decision

Agy CLI returned meta-docs (fail). **Grok GO** is authoritative:  
`agents/collab/2026-07-16-bootstrap/184600Z-agy-fail-grok-go.md`

## Quick start (DEV)

```powershell
powershell -File E:\MyWorkspace\machine-sentinel\scripts\start-dev.ps1
```

Smoke:

- `GET http://127.0.0.1:3350/api/health`
- `GET http://127.0.0.1:3350/api/inventory` — reads MyAgent port/DB JSON registries
- `GET http://127.0.0.1:3350/api/probes` — live listener + known health paths
- `GET http://127.0.0.1:3350/api/pg/pressure` — last line of MyAgent `pg-connections.log`
- `POST http://127.0.0.1:3350/api/pg/pressure/refresh` — runs existing `check-pg-connections.ps1`
- `GET http://127.0.0.1:3350/api/abandon/candidates` — registry-aware classifier (alive PID ≠ abandon; no kills)
- `GET http://127.0.0.1:3350/api/backups/freshness` — `H:/releases` age rules (WARN 7d / CRIT 14d)
- `GET http://127.0.0.1:3350/api/events` — ledger rows

## Must reuse / must not build

See collab brief. Short version: wrap MyAgent checkers and registries; never duplicate them; never auto-kill protected daemons or restart PROD without user GO.

## Ops

See `docs/OPS.md`.
