# OPS — machine-sentinel

## Topology

| Env | Drive | API | UI (later) | Watcher |
|-----|-------|-----|------------|---------|
| DEV | E: | 3350 | 3351 reserved | 3352 reserved (in-process scheduler for now) |
| PREPROD | F: | 4350 | 4351 | 4352 |
| PROD | G: | 5350 | 5351 | 5352 |

DB: shared Postgres `:5432` · `app_machine_sentinel` · schemas `dev|preprod|prod` · tiny Hikari (max 5).

Secrets: `E:\MyAgent\workflow\db\secrets\postgres.env` (`MACHINE_SENTINEL_*`).

## Start / stop (DEV)

```powershell
powershell -File E:\MyWorkspace\machine-sentinel\scripts\start-dev.ps1
```

`start-dev.ps1` clears inherited `SPRING_DATASOURCE_*` (INC-05) and loads role password from secrets.

Stop: Ctrl+C in the start shell. Do **not** kill unrelated Cursor shells that may parent protected ingest wrappers (INC-06).

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
| Backup freshness | 30 min | `sentinel_event` category `backup_freshness` (WARN/CRIT when newest pack stale) |
| Abandon scan | 15 min | `sentinel_event` category `abandon_scan` (WARN if candidates &gt; 0) |

### Abandon classifier (observe-only)

- Alive PIDs and in-progress commands are **never** abandon candidates.
- Protected via MyAgent port-registry path hints + command hints (ingest/MT5/fleet starts, etc.).
- Noise (empty metadata shells) is separated from candidates.
- `sentinel.actions.auto-kill-enabled=false` — no kills.

### Backup freshness

- Root: `H:/releases` (configurable).
- Overall level from **newest** pack age: WARN after 7d, CRIT after 14d.
- Individual packs also labeled; read-only.

## CSS

`clientId=machine-sentinel` registered as **planned**. v0.1 observe APIs are unauthenticated localhost. Wire JWKS before any public edge.
