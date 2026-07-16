package com.delena.machinesentinel.schedule;

import com.delena.machinesentinel.service.AbandonClassifierService;
import com.delena.machinesentinel.service.BackupFreshnessService;
import com.delena.machinesentinel.service.EventLedgerService;
import com.delena.machinesentinel.service.HealthProbeService;
import com.delena.machinesentinel.service.PgPressureService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic observe→ledger. Never mutates fleet processes.
 */
@Component
public class ObserveScheduler {

    private static final Logger log = LoggerFactory.getLogger(ObserveScheduler.class);

    private final PgPressureService pg;
    private final HealthProbeService probes;
    private final BackupFreshnessService backups;
    private final AbandonClassifierService abandon;
    private final EventLedgerService ledger;

    public ObserveScheduler(
            PgPressureService pg,
            HealthProbeService probes,
            BackupFreshnessService backups,
            AbandonClassifierService abandon,
            EventLedgerService ledger) {
        this.pg = pg;
        this.probes = probes;
        this.backups = backups;
        this.abandon = abandon;
        this.ledger = ledger;
    }

    @Scheduled(fixedDelayString = "${sentinel.schedule.pg-ms:300000}", initialDelay = 60_000)
    public void snapshotPgLog() {
        Map<String, Object> snap = pg.latestFromLog();
        String level = String.valueOf(snap.getOrDefault("level", "UNKNOWN"));
        String severity = switch (level) {
            case "CRIT" -> "CRIT";
            case "WARN" -> "WARN";
            default -> "INFO";
        };
        ledger.record("pg_pressure", severity, "pg-connections.log",
                "Scheduled PG log snapshot level=" + level, snap);
        log.debug("PG snapshot level={}", level);
    }

    @Scheduled(fixedDelayString = "${sentinel.schedule.probe-ms:600000}", initialDelay = 90_000)
    public void snapshotProbes() {
        Map<String, Object> snap = probes.probeDevFleet();
        Object down = snap.getOrDefault("down", 0);
        String severity = (down instanceof Number n && n.intValue() > 0) ? "WARN" : "INFO";
        ledger.record("fleet_probe", severity, "port_registry",
                "Scheduled fleet probe down=" + down, Map.of(
                        "ok", snap.get("ok"),
                        "down", down
                ));
    }

    @Scheduled(fixedDelayString = "${sentinel.schedule.backup-ms:1800000}", initialDelay = 120_000)
    public void snapshotBackups() {
        Map<String, Object> snap = backups.assess();
        String level = String.valueOf(snap.getOrDefault("overall_level", "UNKNOWN"));
        String severity = switch (level) {
            case "CRIT" -> "CRIT";
            case "WARN" -> "WARN";
            default -> "INFO";
        };
        ledger.record("backup_freshness", severity, "H:/releases",
                "Scheduled backup freshness overall=" + level, Map.of(
                        "overall_level", level,
                        "newest_pack_at", snap.get("newest_pack_at"),
                        "stale_warn_count", snap.get("stale_warn_count"),
                        "stale_crit_count", snap.get("stale_crit_count")
                ));
    }

    @Scheduled(fixedDelayString = "${sentinel.schedule.abandon-ms:900000}", initialDelay = 150_000)
    public void snapshotAbandon() {
        Map<String, Object> snap = abandon.classifyCursorTerminals();
        int count = snap.get("candidate_count") instanceof Number n ? n.intValue() : 0;
        String severity = count > 0 ? "WARN" : "INFO";
        ledger.record("abandon_scan", severity, "cursor_terminals",
                "Scheduled abandon scan candidates=" + count, Map.of(
                        "candidate_count", count,
                        "protected_count", snap.get("protected_count"),
                        "noise_count", snap.get("noise_count"),
                        "scanned", snap.get("scanned")
                ));
    }
}
