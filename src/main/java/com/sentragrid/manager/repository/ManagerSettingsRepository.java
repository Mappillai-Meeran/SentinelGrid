package com.sentragrid.manager.repository;

import com.sentragrid.manager.entity.ManagerSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManagerSettingsRepository extends JpaRepository<ManagerSettings, Long> {
}
