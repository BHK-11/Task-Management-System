package com.taskos.productivity.repository;

import com.taskos.productivity.entity.GtdInboxItem;
import com.taskos.productivity.entity.InboxState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtdInboxRepository extends JpaRepository<GtdInboxItem, UUID> {
    List<GtdInboxItem> findAllByOwner_IdAndStateOrderByCapturedAtAsc(UUID ownerId, InboxState state);
    Optional<GtdInboxItem> findByIdAndOwner_Id(UUID id, UUID ownerId);
    long countByOwner_IdAndState(UUID ownerId, InboxState state);
}
