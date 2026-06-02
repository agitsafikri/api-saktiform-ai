package com.saktiform.api.service;

import com.saktiform.api.entity.AppConfig;
import com.saktiform.api.repository.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AppConfigService {
    private final AppConfigRepository appConfigRepository;

    public AppConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    public String getConfig(String config){
        var appConfig = appConfigRepository.findAppConfigByConfigNameContainingIgnoreCase(config);
        if (appConfig == null)  throw new RuntimeException("Config not found");
        return appConfig.getConfig();
    }

    public String saveConfig(String configName, String value){
        var appConfig = appConfigRepository.findAppConfigByConfigNameContainingIgnoreCase(configName);
        if (appConfig == null) {
            appConfig = new AppConfig();
            appConfig.setConfigName(configName);
            appConfig.setDeskripsi("API Key OpenAI (Bisa update dari dashboard)");
            appConfig.setCreatedAt(Instant.now());
        }
        appConfig.setConfig(value);
        appConfig.setUpdatedAt(Instant.now());
        appConfigRepository.save(appConfig);

        return appConfig.getConfig();
    }

}
