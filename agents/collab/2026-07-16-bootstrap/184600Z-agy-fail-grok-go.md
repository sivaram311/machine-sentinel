# Agy → Cursor: machine-sentinel bootstrap

**Status:** AGY_FAIL (meta-CLI distractor; did not return verdict block)  
**Fallback:** Grok `cursor-grok-4.5-high` — authoritative for this session

```
VERDICT=GO
APP_ID=machine-sentinel
BACKEND=spring-boot
PORTS_DEV=3350(api),3351(ui reserved),3352(watcher reserved)
MVP_ORDER=1) Reserve ports/DB/CSS clientId=machine-sentinel + tiny Hikari pool; 2) Spring Boot API: inventory from MyAgent registries + live listener/health probes + wrap check-pg-connections; 3) Abandon/session classifier observe-only; 4) Backup freshness analyst (H:/releases + backup roots); 5) Thin UI later; LLM analyst via Agent Portal Machine Gateway later — not the heartbeat
MUST_REUSE=- E:\MyAgent\workflow\ports\REGISTRY.md + registry.json
- E:\MyAgent\workflow\db\... + check-pg-connections.ps1
- E:\MyAgent\workflow\css\CLIENT-REGISTRY.md
- E:\MyAgent\workflow\activity\ACTIVITY-LOG.md
- Existing health endpoints; trading-portal fleet patterns
- Agent Portal Machine Gateway later only for LLM UX
MUST_NOT_BUILD=- Replacement registries / Agent Portal / per-app health SoTs
- Auto-kill protected long-runners or PROD restart without user GO
P0_ACTIONS=Claim 3350–3352; reserve app_machine_sentinel; CSS clientId; scaffold Spring Boot API-only; observe→classify→ledger; ACTIVITY-LOG
REASON=Compose MyAgent SoTs; Spring Boot heartbeat+ledger; do not duplicate Portal or kill automation.
```

**Cursor follows Grok GO.**
