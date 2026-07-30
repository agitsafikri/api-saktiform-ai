package com.saktiform.api.model.blast.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Override konfigurasi worker per-campaign (null = pakai default global).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignConfig {
    private Integer batchSize;
    private Integer delayMs;
    private Integer maxAttempts;
}
