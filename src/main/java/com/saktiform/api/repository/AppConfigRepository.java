package com.saktiform.api.repository;

import com.saktiform.api.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

  AppConfig findAppConfigByConfigNameContainingIgnoreCase(String configName);

  boolean existsByConfigName(String configName);
}