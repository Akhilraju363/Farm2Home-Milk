package com.farm2home.farm.domain.repository;

import com.farm2home.farm.domain.entity.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Short> {
}
