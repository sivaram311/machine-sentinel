package com.delena.machinesentinel.service;

import com.delena.machinesentinel.config.SentinelProperties;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Read-only backup / release-pack freshness over configured roots (default H:/releases).
 */
@Service
public class BackupFreshnessService {

    private final SentinelProperties props;

    public BackupFreshnessService(SentinelProperties props) {
        this.props = props;
    }

    public Map<String, Object> assess() {
        int warnDays = Math.max(1, props.backup().warnAfterDays());
        int critDays = Math.max(warnDays, props.backup().critAfterDays());
        int maxPacks = Math.max(10, props.backup().maxPacks());

        List<String> rootPaths = props.backup().roots();
        if (rootPaths == null || rootPaths.isEmpty()) {
            rootPaths = List.of("H:/releases");
        }

        Instant now = Instant.now();
        Instant warnCutoff = now.minus(Duration.ofDays(warnDays));
        Instant critCutoff = now.minus(Duration.ofDays(critDays));

        List<Map<String, Object>> rootsOut = new ArrayList<>();
        List<Map<String, Object>> allPacks = new ArrayList<>();
        Instant newest = null;

        for (String rootStr : rootPaths) {
            Path root = Path.of(rootStr);
            Map<String, Object> rootRow = new LinkedHashMap<>();
            rootRow.put("path", root.toString());
            rootRow.put("exists", Files.isDirectory(root));
            List<Map<String, Object>> packs = new ArrayList<>();
            if (Files.isDirectory(root)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                    for (Path child : stream) {
                        if (!Files.isDirectory(child)) {
                            continue;
                        }
                        Instant mtime = Files.getLastModifiedTime(child).toInstant();
                        long ageHours = Duration.between(mtime, now).toHours();
                        String packLevel = "OK";
                        if (mtime.isBefore(critCutoff)) {
                            packLevel = "CRIT";
                        } else if (mtime.isBefore(warnCutoff)) {
                            packLevel = "WARN";
                        }
                        Map<String, Object> pack = new LinkedHashMap<>();
                        pack.put("name", child.getFileName().toString());
                        pack.put("path", child.toString());
                        pack.put("last_modified", mtime.toString());
                        pack.put("age_hours", ageHours);
                        pack.put("level", packLevel);
                        packs.add(pack);
                        allPacks.add(pack);
                        if (newest == null || mtime.isAfter(newest)) {
                            newest = mtime;
                        }
                    }
                } catch (Exception e) {
                    rootRow.put("error", e.getMessage());
                }
            }
            packs.sort(Comparator.comparing((Map<String, Object> p) -> String.valueOf(p.get("last_modified"))).reversed());
            rootRow.put("pack_count", packs.size());
            rootRow.put("packs", packs.size() > maxPacks ? packs.subList(0, maxPacks) : packs);
            rootsOut.add(rootRow);
        }

        String overall = "OK";
        if (newest == null) {
            overall = "WARN";
        } else if (newest.isBefore(critCutoff)) {
            overall = "CRIT";
        } else if (newest.isBefore(warnCutoff)) {
            overall = "WARN";
        }

        long staleWarn = allPacks.stream().filter(p -> "WARN".equals(p.get("level"))).count();
        long staleCrit = allPacks.stream().filter(p -> "CRIT".equals(p.get("level"))).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "observe_only");
        out.put("warn_after_days", warnDays);
        out.put("crit_after_days", critDays);
        out.put("overall_level", overall);
        out.put("newest_pack_at", newest != null ? newest.toString() : null);
        out.put("newest_age_hours", newest != null ? Duration.between(newest, now).toHours() : null);
        out.put("stale_warn_count", staleWarn);
        out.put("stale_crit_count", staleCrit);
        out.put("roots", rootsOut);
        out.put("note", "Read-only; does not create/delete backups. Overall level uses newest pack age.");
        return out;
    }
}
