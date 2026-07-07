package com.saktiform.api.service.blast;

import com.saktiform.api.model.blast.event.BlastCampaignStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Memicu generate recipient + queue async setelah campaign di-start (OQ-12).
 * Dipisah dari {@link BlastCampaignService} agar {@code generateRecipientsAndQueue} berjalan pada
 * transaksinya sendiri (cross-bean proxy) → rollback bersih bila gagal, lalu tandai FAILED via
 * {@link BlastFailSafeService} (REQUIRES_NEW) sehingga campaign tidak nyangkut di QUEUED.
 */
@Component
public class BlastCampaignStartListener {

    private static final Logger log = LoggerFactory.getLogger(BlastCampaignStartListener.class);

    private final BlastCampaignService campaignService;
    private final BlastFailSafeService failSafe;

    public BlastCampaignStartListener(BlastCampaignService campaignService, BlastFailSafeService failSafe) {
        this.campaignService = campaignService;
        this.failSafe = failSafe;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStart(BlastCampaignStartEvent event) {
        try {
            campaignService.generateRecipientsAndQueue(event.getCampaignId());
        } catch (Exception e) {
            log.error("Generate recipient/queue gagal untuk campaignId={}", event.getCampaignId(), e);
            failSafe.markCampaignFailed(event.getCampaignId());
        }
    }
}
