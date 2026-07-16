# Soak — machine-sentinel 0.1 (DEV)

| Field | Value |
|-------|-------|
| Session | machine-sentinel-bootstrap-2026-07-16 |
| Started (IST) | 2026-07-17 01:11 |
| Tip at start | `33b190a` (GitHub main) |
| API | http://127.0.0.1:3350 |
| UI | http://127.0.0.1:3351 |
| Mode | observe-only |

## Baseline (2026-07-16T19:38Z / IST ~01:08)

| Signal | Value |
|--------|-------|
| `/api/health` | UP |
| UI | 200 |
| Ledger rows (recent window) | 22 |
| Categories seen | `pg_pressure`, `fleet_probe`, `abandon_scan`, `backup_freshness` |
| PG | OK · app_conns≈14 / max 150 |
| Backup overall | OK |
| Abandon candidates | 0 |
| Fleet probes | ok=5 down=5 (**expected** — reserved DEV ports not all bound) |

## Pass criteria (`soak_met=true` after ≥20h uptime preferred; ≥4h minimum for early GO)

1. Process still serving `/api/health` = UP (no crash loop).
2. Scheduled categories keep appending (at least one new row per category after start).
3. No `CRIT` on `pg_pressure` or `backup_freshness` overall without operator note.
4. Abandon scan stays observe-only (`auto_kill` never enabled); candidates may be >0 but no kills.
5. Hikari stays tiny — shared PG app_conns not climbing unboundedly from sentinel alone.

## Non-goals

- Do not kill Cursor shells / fleet parents to “clean” soak (INC-06).
- Fleet `down>0` alone is not FAIL while ports remain reserved-unused.

## Closeout

```powershell
powershell -File E:\MyWorkspace\machine-sentinel\scripts\check-soak.ps1
```

Record result in this file + ACTIVITY-LOG. Set `soak_met=true|false`.
