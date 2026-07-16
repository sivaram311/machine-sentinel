package com.delena.machinesentinel.web;

import com.delena.machinesentinel.domain.SentinelEvent;
import com.delena.machinesentinel.service.AbandonClassifierService;
import com.delena.machinesentinel.service.BackupFreshnessService;
import com.delena.machinesentinel.service.EventLedgerService;
import com.delena.machinesentinel.service.HealthProbeService;
import com.delena.machinesentinel.service.PgPressureService;
import com.delena.machinesentinel.service.RegistryInventoryService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SentinelApiController {

    private final RegistryInventoryService inventory;
    private final HealthProbeService probes;
    private final PgPressureService pg;
    private final AbandonClassifierService abandon;
    private final BackupFreshnessService backups;
    private final EventLedgerService ledger;

    public SentinelApiController(
            RegistryInventoryService inventory,
            HealthProbeService probes,
            PgPressureService pg,
            AbandonClassifierService abandon,
            BackupFreshnessService backups,
            EventLedgerService ledger) {
        this.inventory = inventory;
        this.probes = probes;
        this.pg = pg;
        this.abandon = abandon;
        this.backups = backups;
        this.ledger = ledger;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "machine-sentinel",
                "mode", "observe_only",
                "ts", Instant.now().toString()
        );
    }

    @GetMapping("/inventory")
    public Map<String, Object> inventory() {
        return inventory.inventory();
    }

    @GetMapping("/probes")
    public Map<String, Object> probes() {
        return probes.probeDevFleet();
    }

    @GetMapping("/pg/pressure")
    public Map<String, Object> pgPressure() {
        return pg.latestFromLog();
    }

    @PostMapping("/pg/pressure/refresh")
    public Map<String, Object> pgPressureRefresh() {
        Map<String, Object> result = pg.runCheckScript();
        String level = String.valueOf(result.getOrDefault("status", "UNKNOWN"));
        String severity = switch (level) {
            case "CRIT" -> "CRIT";
            case "WARN" -> "WARN";
            default -> "INFO";
        };
        ledger.record("pg_pressure", severity, "check-pg-connections.ps1",
                "PG pressure " + level, result);
        return result;
    }

    @GetMapping("/abandon/candidates")
    public Map<String, Object> abandonCandidates() {
        Map<String, Object> result = abandon.classifyCursorTerminals();
        int count = asInt(result.get("candidate_count"));
        String severity = count > 0 ? "WARN" : "INFO";
        ledger.record("abandon_scan", severity, "cursor_terminals",
                "Abandon scan candidates=" + count, Map.of(
                        "candidate_count", count,
                        "protected_count", result.getOrDefault("protected_count", 0),
                        "noise_count", result.getOrDefault("noise_count", 0),
                        "scanned", result.getOrDefault("scanned", 0)
                ));
        return result;
    }

    @GetMapping("/backups/freshness")
    public Map<String, Object> backupFreshness() {
        Map<String, Object> result = backups.assess();
        String level = String.valueOf(result.getOrDefault("overall_level", "UNKNOWN"));
        if ("WARN".equals(level) || "CRIT".equals(level)) {
            ledger.record("backup_freshness", level, "H:/releases",
                    "Backup freshness overall=" + level, Map.of(
                            "overall_level", level,
                            "newest_pack_at", result.get("newest_pack_at"),
                            "stale_warn_count", result.get("stale_warn_count"),
                            "stale_crit_count", result.get("stale_crit_count")
                    ));
        }
        return result;
    }

    @GetMapping("/events")
    public List<SentinelEvent> events() {
        return ledger.recent();
    }

    @GetMapping("/ops/status")
    public Map<String, Object> opsStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("app", "machine-sentinel");
        out.put("mode", "observe_only");
        out.put("auto_kill_enabled", false);
        out.put("pg", pg.latestFromLog());
        Map<String, Object> probeSummary = probes.probeDevFleet();
        out.put("probes_ok", probeSummary.get("ok"));
        out.put("probes_down", probeSummary.get("down"));
        Map<String, Object> bak = backups.assess();
        out.put("backup_overall_level", bak.get("overall_level"));
        out.put("backup_newest_pack_at", bak.get("newest_pack_at"));
        return out;
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }
}
