package com.delena.machinesentinel.service;

import com.delena.machinesentinel.config.SentinelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Live listener + HTTP health probes against reserved ports.
 * Does not invent health semantics — probes common existing endpoints.
 */
@Service
public class HealthProbeService {

    private static final Set<String> PROBE_ROLES = Set.of("http", "api");
    private static final List<String> HEALTH_PATHS = List.of(
            "/api/health",
            "/actuator/health",
            "/health",
            "/api/ops/status"
    );

    private final SentinelProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public HealthProbeService(SentinelProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, props.probe().timeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Map<String, Object> probeDevFleet() {
        List<Map<String, Object>> results = new ArrayList<>();
        Path path = Path.of(props.ports().registryJson());
        int ok = 0;
        int down = 0;
        int skip = 0;
        if (Files.isRegularFile(path)) {
            try {
                JsonNode root = mapper.readTree(path.toFile());
                for (JsonNode n : root.path("reservations")) {
                    if (!"dev".equalsIgnoreCase(text(n, "env"))) {
                        continue;
                    }
                    String role = text(n, "role");
                    if (role == null || !PROBE_ROLES.contains(role.toLowerCase())) {
                        skip++;
                        continue;
                    }
                    int port = n.path("port").asInt();
                    Map<String, Object> row = probePort(port, text(n, "appId"), role);
                    results.add(row);
                    if ("up".equals(row.get("status"))) {
                        ok++;
                    } else if ("down".equals(row.get("status"))) {
                        down++;
                    } else {
                        skip++;
                    }
                }
                for (JsonNode n : root.path("shared")) {
                    String role = text(n, "role");
                    if (role != null && PROBE_ROLES.contains(role.toLowerCase())) {
                        int port = n.path("port").asInt();
                        Map<String, Object> row = probePort(port, text(n, "appId"), role);
                        results.add(row);
                        if ("up".equals(row.get("status"))) {
                            ok++;
                        } else if ("down".equals(row.get("status"))) {
                            down++;
                        }
                    }
                }
            } catch (Exception e) {
                return Map.of("error", e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("down", down);
        out.put("skipped", skip);
        out.put("probes", results);
        return out;
    }

    private Map<String, Object> probePort(int port, String appId, String role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("port", port);
        row.put("app_id", appId);
        row.put("role", role);
        row.put("listening", isListening(port));
        if (!Boolean.TRUE.equals(row.get("listening"))) {
            row.put("status", "down");
            row.put("detail", "not_listening");
            return row;
        }
        Duration timeout = Duration.ofMillis(Math.max(500, props.probe().timeoutMs()));
        for (String path : HEALTH_PATHS) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(timeout)
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 500) {
                    row.put("status", code < 400 ? "up" : "degraded");
                    row.put("health_path", path);
                    row.put("http_status", code);
                    return row;
                }
            } catch (Exception ignored) {
                // try next path
            }
        }
        row.put("status", "up");
        row.put("detail", "listening_no_known_health_path");
        return row;
    }

    private static boolean isListening(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
