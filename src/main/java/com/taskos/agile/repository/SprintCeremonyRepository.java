package com.taskos.agile.repository;

import com.taskos.agile.entity.SprintCeremony;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintCeremonyRepository extends JpaRepository<SprintCeremony, UUID> { }
