# OPS — machine-sentinel

## Topology

| Env | Drive | API | UI | Watcher |
|-----|-------|-----|----|---------|
| DEV | E: | 3350 active | 3351 active | 3352 reserved (in-process scheduler for now) |
| PREPROD | F: | 4350 | 4351 | 4352 |
| PROD | G: | 5350 | 5351 | 5352 |

DB: shared Postgres `:5432` · `app_machine_sentinel` · schemas `dev|preprod|prod` · tiny Hikari (max 5).

Secrets: `E:\MyAgent\workflow\db\secrets\postgres.env` (`MACHINE_SENTINEL_*`).

## Start / stop (DEV)

```powershell
powershell -File E:\MyWorkspace\machine-sentinel\scripts\start-dev.ps1
powershell -File E:\MyWorkspace\machine-sentinel\scripts\start-ui.ps1
```

- API: http://127.0.0.1:3350/api/health  
- UI: http://127.0.0.1:3351/

`start-dev.ps1` clears inherited `SPRING_DATASOURCE_*` (INC-05) and loads role password from secrets.

Stop: Ctrl+C in the start shells. Do **not** kill unrelated Cursor shells that may parent protected ingest wrappers (INC-06).

## Session-0 restart persistence (SYSTEM)

To ensure machine-sentinel is restarted after unexpected reboots (or planned OS reboots), register Windows startup tasks that run in **Session 0** as `SYSTEM`.

```powershell
powershell -File E:\MyWorkspace\machine-sentinel\scripts\register-session0-tasks.ps1
```

- API task: `MachineSentinelAPI` (runs `run-api-session0.ps1`)
- UI task: `MachineSentinelUI` (runs `run-ui-session0.ps1`)

These tasks trigger on boot and avoid forcing immediate starts (so they won't duplicate the currently running DEV processes).

## Soak (DEV 0.1)

See [`SOAK-0.1.md`](./SOAK-0.1.md). Started 2026-07-17 IST. Sample anytime:

```powershell
powershell -File E:\MyWorkspace\machine-sentinel\scripts\check-soak.ps1
```

Fleet `probes_down>0` for unused reserved ports is expected and not a soak failure.

## CSS

`clientId=machine-sentinel` **active** on CSS DEV `:9000`.

- Public: `/api/health`, `/actuator/health`
- All other `/api/**` require Bearer JWT validated via JWKS (`sentinel.security.jwk-set-uri`)
- Audience/client must be `machine-sentinel`
- UI uses `POST http://127.0.0.1:9000/auth/login` then calls the API
- Break-glass: `sentinel.security.enabled=false` (DEV only)

## Safety

- `sentinel.actions.auto-kill-enabled=false` (hard default)
- Abandon classifier is **observe-only**
- PG pressure reuses MyAgent script/log — does not redefine thresholds
- No PROD restart automation

## Scheduled observe

| Job | Default interval | Writes |
|-----|------------------|--------|
| PG log snapshot | 5 min | `sentinel_event` category `pg_pressure` |
| Fleet probe summary | 10 min | `sentinel_event` category `fleet_probe` |
| Backup freshness | 30 min | `sentinel_event` category `backup_freshness` |
| Abandon scan | 15 min | `sentinel_event` category `abandon_scan` |

### Abandon classifier (observe-only)

- Alive PIDs and in-progress commands are **never** abandon candidates.
- Protected via MyAgent port-registry path hints + command hints.
- Noise (empty metadata shells) is separated from candidates.

### Backup freshness

- Root: `H:/releases` (configurable).
- Overall level from **newest** pack age: WARN after 7d, CRIT after 14d.
