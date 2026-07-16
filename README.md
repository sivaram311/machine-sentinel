# Machine Sentinel

Registry-aware **observe → classify → ledger** for this Windows machine.  
Composes MyAgent SoTs — does **not** replace ports/DB/CSS/activity registries or Agent Portal.

| | |
|--|--|
| App id | `machine-sentinel` |
| Backend | Spring Boot 3.3 (Java 21) |
| DEV API | `:3350` (CSS JWKS; `/api/health` public) |
| DEV UI | `:3351` thin ops console |
| DB | `app_machine_sentinel` / schema `dev` |
| CSS | `clientId=machine-sentinel` **active** |
| Mode | **observe-only** — no auto-kill (INC-06) |

## Decision

Agy CLI returned meta-docs (fail). **Grok GO** is authoritative:  
`agents/collab/2026-07-16-bootstrap/184600Z-agy-fail-grok-go.md`

## Quick start (DEV)

```powershell
powershell -File E:\MyWorkspace\machine-sentinel\scripts\start-dev.ps1
powershell -File E:\MyWorkspace\machine-sentinel\scripts\start-ui.ps1
```

- UI: http://127.0.0.1:3351/ (CSS `admin` login → Bearer to API)
- `GET http://127.0.0.1:3350/api/health` (public)
- Other `/api/*` require CSS JWT (`aud`/`client_id` = `machine-sentinel`)

## Must reuse / must not build

See collab brief. Short version: wrap MyAgent checkers and registries; never duplicate them; never auto-kill protected daemons or restart PROD without user GO.

## Ops

See `docs/OPS.md`.
