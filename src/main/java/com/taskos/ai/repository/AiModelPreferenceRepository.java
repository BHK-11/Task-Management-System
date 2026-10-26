package com.taskos.ai.repository;

import com.taskos.ai.entity.AiModelPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelPreferenceRepository extends JpaRepository<AiModelPreference, UUID> {}
