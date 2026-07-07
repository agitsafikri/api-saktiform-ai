package com.saktiform.api.service.blast.worker;

import com.saktiform.api.service.blast.BlastCampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rekonsiliasi counter campaign RUNNING secara periodik dari blast_message untuk koreksi drift (OQ-9).
 */
@Component
public class BlastCounterReconciler {

    private static final Logger log = LoggerFactory.getLogger(BlastCounterReconciler.class);

    private final BlastCampaignService campaignService;

    public BlastCounterReconciler(BlastCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @Scheduled(fixedDelayString = "${blast.worker.reconcile-interval-ms:300000}")
    public void reconcile() {
        try {
            campaignService.reconcileRunning();
        } catch (Exception e) {
            log.error("Blast counter reconciler error", e);
        }
    }
}
