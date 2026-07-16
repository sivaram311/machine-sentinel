package com.delena.machinesentinel.service;

import com.delena.machinesentinel.config.SentinelProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Wraps the existing MyAgent PG checker — does not re-implement thresholds.
 */
@Service
public class PgPressureService {

    private static final Pattern LINE = Pattern.compile(
            "^(?<ts>\\S+)\\s+level=(?<level>\\w+)\\s+app_conns=(?<app>\\d+)\\s+total=(?<total>\\d+)\\s+max_connections=(?<max>\\d+).*");

    private final SentinelProperties props;

    public PgPressureService(SentinelProperties props) {
        this.props = props;
    }

    public Map<String, Object> latestFromLog() {
        Map<String, Object> out = new LinkedHashMap<>();
        Path log = Path.of(props.pg().connectionsLog());
        out.put("source", "myagent_pg_connections_log");
        out.put("path", log.toString());
        out.put("readable", Files.isRegularFile(log));
        if (!Files.isRegularFile(log)) {
            out.put("status", "missing_log");
            return out;
        }
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            String last = null;
            for (int i = lines.size() - 1; i >= 0; i--) {
                String candidate = lines.get(i).trim();
                if (!candidate.isEmpty()) {
                    last = candidate;
                    break;
                }
            }
            out.put("raw", last);
            if (last != null) {
                Matcher m = LINE.matcher(last);
                if (m.find()) {
                    out.put("ts", m.group("ts"));
                    out.put("level", m.group("level"));
                    out.put("app_conns", Integer.parseInt(m.group("app")));
                    out.put("total", Integer.parseInt(m.group("total")));
                    out.put("max_connections", Integer.parseInt(m.group("max")));
                }
            }
        } catch (Exception e) {
            out.put("error", e.getMessage());
        }
        return out;
    }

    public Map<String, Object> runCheckScript() {
        Map<String, Object> out = new LinkedHashMap<>();
        Path script = Path.of(props.pg().checkScript());
        out.put("source", "myagent_check_pg_connections_ps1");
        out.put("script", script.toString());
        if (!Files.isRegularFile(script)) {
            out.put("status", "missing_script");
            return out;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(45, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                out.put("status", "timeout");
                return out;
            }
            List<String> stdout = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    stdout.add(line);
                }
            }
            out.put("exit_code", p.exitValue());
            out.put("stdout", stdout);
            out.put("status", switch (p.exitValue()) {
                case 0 -> "OK";
                case 1 -> "WARN";
                case 2 -> "CRIT";
                default -> "UNKNOWN";
            });
            out.put("log_snapshot", latestFromLog());
        } catch (Exception e) {
            out.put("status", "error");
            out.put("error", e.getMessage());
        }
        return out;
    }
}
