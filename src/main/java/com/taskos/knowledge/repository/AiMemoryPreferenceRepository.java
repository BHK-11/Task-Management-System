package com.taskos.knowledge.repository;

import com.taskos.knowledge.entity.AiMemoryPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMemoryPreferenceRepository extends JpaRepository<AiMemoryPreference, UUID> {}
