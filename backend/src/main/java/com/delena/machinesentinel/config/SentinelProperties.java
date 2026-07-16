package com.delena.machinesentinel.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinel")
public record SentinelProperties(
        MyAgent myagent,
        Ports ports,
        Db db,
        Pg pg,
        Probe probe,
        Abandon abandon,
        Backup backup,
        Actions actions
) {
    public record MyAgent(String root) {}
    public record Ports(String registryJson) {}
    public record Db(String registryJson) {}
    public record Pg(String connectionsLog, String checkScript) {}
    public record Probe(long timeoutMs) {}
    public record Abandon(int idleMinutes, List<String> terminalDirs) {}
    public record Backup(List<String> roots, int warnAfterDays, int critAfterDays, int maxPacks) {}
    public record Actions(boolean autoKillEnabled) {}
}
