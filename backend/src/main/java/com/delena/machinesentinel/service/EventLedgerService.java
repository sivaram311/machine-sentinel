package com.delena.machinesentinel.service;

import com.delena.machinesentinel.domain.SentinelEvent;
import com.delena.machinesentinel.repo.SentinelEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventLedgerService {

    private final SentinelEventRepository repo;
    private final ObjectMapper mapper;

    public EventLedgerService(SentinelEventRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public SentinelEvent record(String category, String severity, String source, String summary, Object detail) {
        SentinelEvent e = new SentinelEvent();
        e.setCategory(category);
        e.setSeverity(severity);
        e.setSource(source);
        e.setSummary(summary.length() > 512 ? summary.substring(0, 509) + "..." : summary);
        e.setActionTaken("observe_only");
        if (detail != null) {
            try {
                e.setDetailJson(mapper.writeValueAsString(detail));
            } catch (Exception ex) {
                e.setDetailJson(Map.of("error", "serialize_failed").toString());
            }
        }
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public List<SentinelEvent> recent() {
        return repo.findTop50ByOrderByOccurredAtDesc();
    }
}
