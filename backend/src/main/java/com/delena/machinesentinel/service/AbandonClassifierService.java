package com.delena.machinesentinel.service;

import com.delena.machinesentinel.config.SentinelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Observe-only abandon/session classifier (INC-06: never kill).
 * Registry-aware protection + live PID check to cut false positives.
 */
@Service
public class AbandonClassifierService {

    private static final Pattern PID = Pattern.compile("(?m)^pid:\\s*(\\d+)");
    private static final Pattern CWD = Pattern.compile("(?m)^cwd:\\s*(.+)");
    private static final Pattern LAST_CMD = Pattern.compile("(?m)^last_command:\\s*(.+)");
    private static final Pattern EXIT = Pattern.compile("(?m)^last_exit_code:\\s*(\\S+)");
    private static final Pattern RUNNING_MS = Pattern.compile("(?m)^running_for_ms:\\s*(\\d+)");
    private static final Pattern ACTIVE_CMD = Pattern.compile("(?m)^active_command:\\s*(.+)");
    private static final Pattern PATH_IN_NOTES = Pattern.compile(
            "[EFGH]:\\\\(?:[^\\\\\\s|;,]+\\\\)*[^\\\\\\s|;,]+", Pattern.CASE_INSENSITIVE);

    /** Only match in cwd / last_command / active_command — not full transcript noise. */
    private static final List<String> COMMAND_PROTECT_HINTS = List.of(
            "ingest", "mt5", "trading-portal", "machine-sentinel",
            "start-preprod", "start-prod", "start-dev.ps1",
            "check-fleet", "playwright", "spring-boot:run",
            "centralized-security", "agent-portal", "css-next"
    );

    private final SentinelProperties props;
    private final ObjectMapper mapper;

    public AbandonClassifierService(SentinelProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public Map<String, Object> classifyCursorTerminals() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "observe_only");
        out.put("auto_kill_enabled", props.actions().autoKillEnabled());
        out.put("idle_minutes_threshold", props.abandon().idleMinutes());

        Set<String> protectedRoots = loadProtectedRootsFromRegistry();
        out.put("protected_roots_count", protectedRoots.size());

        List<Map<String, Object>> candidates = new ArrayList<>();
        List<Map<String, Object>> protectedSessions = new ArrayList<>();
        List<Map<String, Object>> noise = new ArrayList<>();
        List<Map<String, Object>> active = new ArrayList<>();
        int scanned = 0;

        List<String> dirs = props.abandon().terminalDirs();
        if (dirs == null || dirs.isEmpty()) {
            String home = System.getProperty("user.home");
            dirs = List.of(
                    Path.of(home, ".cursor", "projects", "E-MyWorkspace", "terminals").toString(),
                    Path.of(home, ".cursor", "projects", "E-MyAgent", "terminals").toString()
            );
        }
        out.put("terminals_dirs", dirs);

        Instant cutoff = Instant.now().minus(Duration.ofMinutes(Math.max(15, props.abandon().idleMinutes())));

        for (String dir : dirs) {
            Path terminals = Path.of(dir);
            if (!Files.isDirectory(terminals)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(terminals, "*.txt")) {
                for (Path file : stream) {
                    scanned++;
                    Map<String, Object> row = classifyFile(file, cutoff, protectedRoots);
                    String classification = String.valueOf(row.get("classification"));
                    switch (classification) {
                        case "abandon_candidate" -> candidates.add(row);
                        case "protected" -> protectedSessions.add(row);
                        case "noise" -> noise.add(row);
                        default -> active.add(row);
                    }
                }
            } catch (IOException e) {
                out.put("error", e.getMessage());
            }
        }

        out.put("scanned", scanned);
        out.put("candidate_count", candidates.size());
        out.put("protected_count", protectedSessions.size());
        out.put("noise_count", noise.size());
        out.put("active_or_recent_count", active.size());
        out.put("candidates", candidates);
        out.put("protected_sessions", protectedSessions.size() > 40
                ? protectedSessions.subList(0, 40) : protectedSessions);
        out.put("note", "No kills performed. Alive PIDs and registry app paths are never abandon candidates.");
        return out;
    }

    private Map<String, Object> classifyFile(Path file, Instant cutoff, Set<String> protectedRoots)
            throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        String head = raw.length() > 6000 ? raw.substring(0, 6000) : raw;
        Instant modified = Files.getLastModifiedTime(file).toInstant();

        String pidStr = match(PID, head);
        String cwd = match(CWD, head);
        String lastCmd = match(LAST_CMD, head);
        String exit = match(EXIT, head);
        String activeCmd = match(ACTIVE_CMD, head);
        String runningMs = match(RUNNING_MS, head);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("file", file.getFileName().toString());
        row.put("dir", file.getParent() != null ? file.getParent().getFileName().toString() : null);
        row.put("modified_at", modified.toString());
        row.put("pid", pidStr);
        row.put("cwd", cwd);
        row.put("last_command", lastCmd);
        row.put("last_exit_code", exit);
        row.put("active_command", activeCmd);

        boolean pidAlive = isPidAlive(pidStr);
        row.put("pid_alive", pidAlive);

        boolean looksRunning = runningMs != null
                || (activeCmd != null && !activeCmd.isBlank())
                || (pidAlive && exit == null);
        boolean stale = modified.isBefore(cutoff);
        String protectReason = protectReason(cwd, lastCmd, activeCmd, protectedRoots);

        row.put("looks_running", looksRunning);
        row.put("stale", stale);

        if (isNoise(raw, lastCmd, activeCmd, pidStr)) {
            row.put("classification", "noise");
            return row;
        }
        if (protectReason != null) {
            row.put("protected", true);
            row.put("protect_reason", protectReason);
            row.put("classification", "protected");
            return row;
        }
        // Live process ⇒ never abandon (INC-06: wrappers may parent ingest)
        if (pidAlive || looksRunning) {
            row.put("classification", "active_or_recent");
            return row;
        }
        if (stale && (lastCmd != null || exit != null)) {
            row.put("classification", "abandon_candidate");
            row.put("protected", false);
            return row;
        }
        row.put("classification", "active_or_recent");
        return row;
    }

    private static boolean isNoise(String raw, String lastCmd, String activeCmd, String pid) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        if (raw.length() < 80 && lastCmd == null && activeCmd == null) {
            return true;
        }
        return pid == null && lastCmd == null && activeCmd == null && raw.length() < 200;
    }

    private String protectReason(String cwd, String lastCmd, String activeCmd, Set<String> roots) {
        String blob = ((cwd == null ? "" : cwd) + "\n"
                + (lastCmd == null ? "" : lastCmd) + "\n"
                + (activeCmd == null ? "" : activeCmd)).toLowerCase(Locale.ROOT);
        for (String root : roots) {
            if (!root.isBlank() && blob.contains(root.toLowerCase(Locale.ROOT))) {
                return "registry_path:" + root;
            }
        }
        for (String hint : COMMAND_PROTECT_HINTS) {
            if (blob.contains(hint.toLowerCase(Locale.ROOT))) {
                return "command_hint:" + hint;
            }
        }
        return null;
    }

    private Set<String> loadProtectedRootsFromRegistry() {
        Set<String> roots = new LinkedHashSet<>();
        roots.add("E:\\MyWorkspace\\trading-portal");
        roots.add("E:\\MyWorkspace\\machine-sentinel");
        roots.add("E:\\MyWorkspace\\centralized-security-system");
        roots.add("E:\\MyWorkspace\\agent-portal");
        roots.add("F:\\apps");
        roots.add("G:\\apps");
        Path path = Path.of(props.ports().registryJson());
        if (!Files.isRegularFile(path)) {
            return roots;
        }
        try {
            JsonNode root = mapper.readTree(path.toFile());
            for (JsonNode n : root.path("reservations")) {
                extractPaths(text(n, "notes"), roots);
                extractPaths(text(n, "path"), roots);
            }
            for (JsonNode n : root.path("shared")) {
                extractPaths(text(n, "notes"), roots);
            }
        } catch (Exception ignored) {
            // keep defaults
        }
        return roots;
    }

    private static void extractPaths(String notes, Set<String> roots) {
        if (notes == null || notes.isBlank()) {
            return;
        }
        Matcher m = PATH_IN_NOTES.matcher(notes);
        while (m.find()) {
            roots.add(m.group());
        }
    }

    private static boolean isPidAlive(String pidStr) {
        if (pidStr == null || pidStr.isBlank()) {
            return false;
        }
        try {
            long pid = Long.parseLong(pidStr.trim());
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            return handle.isPresent() && handle.get().isAlive();
        } catch (Exception e) {
            return false;
        }
    }

    private static String match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
