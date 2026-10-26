package com.taskos.agile.repository;

import com.taskos.agile.entity.AgileProjectSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgileProjectSettingsRepository extends JpaRepository<AgileProjectSettings, UUID> { }
