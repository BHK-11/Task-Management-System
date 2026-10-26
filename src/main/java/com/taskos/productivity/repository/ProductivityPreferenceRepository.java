package com.taskos.productivity.repository;

import com.taskos.productivity.entity.ProductivityPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductivityPreferenceRepository extends JpaRepository<ProductivityPreference, UUID> {
}
