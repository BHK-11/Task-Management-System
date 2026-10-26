package com.taskos.browser.service;

import com.taskos.browser.entity.BrowserJobStatus;
import com.taskos.browser.entity.BrowserObservationJob;
import com.taskos.browser.repository.BrowserObservationJobRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrowserObservationJobService {
    private static final int MAX_HISTORY = 100;

    private final BrowserObservationJobRepository jobs;
    private final Clock clock;

    public BrowserObservationJobService(BrowserObservationJobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BrowserObservationJob> recent(UUID ownerId) {
        return jobs.findAllByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, 20));
    }

    @Transactional(readOnly = true)
    public void ensureCapacity(UUID ownerId) {
        if (jobs.countByOwnerId(ownerId) >= MAX_HISTORY)
            throw new BrowserWorkerException("Browser history reached its 100-entry limit. Delete an older entry first.",
                    "BROWSER_HISTORY_LIMIT");
    }

    @Transactional
    public BrowserObservationJob record(UUID ownerId, String origin, BrowserJobStatus status,
            String title, String errorCode) {
        if (jobs.countByOwnerId(ownerId) >= MAX_HISTORY)
            throw new BrowserWorkerException("Browser history reached its 100-entry limit. Delete an older entry first.",
                    "BROWSER_HISTORY_LIMIT");
        String safeTitle = title == null || title.isBlank() ? null : title.strip().substring(0, Math.min(240, title.strip().length()));
        return jobs.save(BrowserObservationJob.record(ownerId, origin, status, safeTitle, errorCode, clock.instant()));
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        BrowserObservationJob job = jobs.findByIdAndOwnerId(id, ownerId).orElseThrow(BrowserObservationNotFoundException::new);
        jobs.delete(job);
    }
}
