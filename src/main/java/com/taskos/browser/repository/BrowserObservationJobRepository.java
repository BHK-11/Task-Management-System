package com.taskos.browser.repository;

import com.taskos.browser.entity.BrowserObservationJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrowserObservationJobRepository extends JpaRepository<BrowserObservationJob, UUID> {
    List<BrowserObservationJob> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId, PageRequest page);
    Optional<BrowserObservationJob> findByIdAndOwnerId(UUID id, UUID ownerId);
    long countByOwnerId(UUID ownerId);
}
