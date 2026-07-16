package com.delena.machinesentinel.service;

import com.delena.machinesentinel.config.SentinelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Reads MyAgent port/DB registries — does not own or rewrite them.
 */
@Service
public class RegistryInventoryService {

    private final SentinelProperties props;
    private final ObjectMapper mapper;

    public RegistryInventoryService(SentinelProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public Map<String, Object> inventory() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "myagent_registries");
        out.put("ports", readPorts());
        out.put("databases", readDatabases());
        return out;
    }

    private Map<String, Object> readPorts() {
        Map<String, Object> result = new LinkedHashMap<>();
        Path path = Path.of(props.ports().registryJson());
        result.put("path", path.toString());
        result.put("readable", Files.isRegularFile(path));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (Files.isRegularFile(path)) {
            try {
                JsonNode root = mapper.readTree(path.toFile());
                for (JsonNode n : root.path("shared")) {
                    rows.add(portRow(n));
                }
                for (JsonNode n : root.path("reservations")) {
                    rows.add(portRow(n));
                }
                result.put("updated", text(root, "updated"));
            } catch (Exception e) {
                result.put("error", e.getMessage());
            }
        }
        result.put("count", rows.size());
        result.put("entries", rows);
        return result;
    }

    private Map<String, Object> readDatabases() {
        Map<String, Object> result = new LinkedHashMap<>();
        Path path = Path.of(props.db().registryJson());
        result.put("path", path.toString());
        result.put("readable", Files.isRegularFile(path));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (Files.isRegularFile(path)) {
            try {
                JsonNode root = mapper.readTree(path.toFile());
                for (JsonNode n : root.path("applications")) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("app_id", text(n, "appId"));
                    row.put("database", text(n, "database"));
                    row.put("status", text(n, "status"));
                    rows.add(row);
                }
                result.put("updated", text(root, "updated"));
            } catch (Exception e) {
                result.put("error", e.getMessage());
            }
        }
        result.put("count", rows.size());
        result.put("entries", rows);
        return result;
    }

    private static Map<String, Object> portRow(JsonNode n) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("port", n.path("port").asInt());
        row.put("app_id", text(n, "appId"));
        row.put("env", text(n, "env"));
        row.put("role", text(n, "role"));
        row.put("status", text(n, "status"));
        return row;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
