package com.saktiform.api.service.blast;

import com.saktiform.api.model.blast.event.BlastImportUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Memicu analisis kontak async setelah upload (OQ-1). Dipisah dari {@link BlastAnalysisService} agar
 * {@code analyze} berjalan pada transaksinya sendiri (cross-bean proxy) → rollback bersih bila gagal,
 * lalu tandai import FAILED via {@link BlastFailSafeService} (REQUIRES_NEW).
 */
@Component
public class BlastImportAnalyzeListener {

    private static final Logger log = LoggerFactory.getLogger(BlastImportAnalyzeListener.class);

    private final BlastAnalysisService analysisService;
    private final BlastFailSafeService failSafe;

    public BlastImportAnalyzeListener(BlastAnalysisService analysisService, BlastFailSafeService failSafe) {
        this.analysisService = analysisService;
        this.failSafe = failSafe;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUploaded(BlastImportUploadedEvent event) {
        try {
            analysisService.analyze(event.getImportId(), event.getWorkspaceId());
        } catch (Exception e) {
            log.error("Analisis kontak gagal untuk importId={}", event.getImportId(), e);
            failSafe.markImportFailed(event.getImportId());
        }
    }
}
