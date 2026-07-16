# Pre-work approval — machine-sentinel

**Status:** **GO**  
**Decision maker:** Grok (`cursor-grok-4.5-high`) after Agy meta-CLI fail  
**Evidence:** `agents/collab/2026-07-16-bootstrap/184600Z-agy-fail-grok-go.md`  
**Date:** 2026-07-16 / 2026-07-17 IST

## Coding allowed now

- Ports 3350–3352 (+ PREPROD/PROD mirrors) — reserved
- DB `app_machine_sentinel` — **provisioned**
- CSS `clientId=machine-sentinel` — planned (wire later)
- Spring Boot API observe → classify → ledger on `:3350`

## Forbidden without later GO

- Auto-kill of processes / PROD restart automation
- Replacement of MyAgent registries or Agent Portal
- `git push` / tag without Reviewer SIGN-OFF (#17)
- Promote Q1/Q2 without evidence + EM GO
