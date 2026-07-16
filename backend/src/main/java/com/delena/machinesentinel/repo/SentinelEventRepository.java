package com.delena.machinesentinel.repo;

import com.delena.machinesentinel.domain.SentinelEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentinelEventRepository extends JpaRepository<SentinelEvent, Long> {
    List<SentinelEvent> findTop50ByOrderByOccurredAtDesc();
}
