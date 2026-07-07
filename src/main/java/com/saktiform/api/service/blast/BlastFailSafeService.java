package com.saktiform.api.service.blast;

import com.saktiform.api.model.blast.enums.CampaignStatus;
import com.saktiform.api.model.blast.enums.ImportStatus;
import com.saktiform.api.repository.BlastCampaignRepository;
import com.saktiform.api.repository.BlastImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Menandai FAILED dalam transaksi {@code REQUIRES_NEW} sehingga tetap tersimpan meskipun transaksi
 * pemanggil rollback (mis. generate recipient/queue atau analisis gagal). Mencegah state nyangkut.
 */
@Service
public class BlastFailSafeService {

    private final BlastCampaignRepository campaignRepository;
    private final BlastImportRepository importRepository;

    public BlastFailSafeService(BlastCampaignRepository campaignRepository,
                                BlastImportRepository importRepository) {
        this.campaignRepository = campaignRepository;
        this.importRepository = importRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCampaignFailed(UUID campaignId) {
        campaignRepository.findById(campaignId).ifPresent(c -> {
            c.setStatus(CampaignStatus.FAILED.name());
            c.setUpdatedAt(Instant.now());
            campaignRepository.save(c);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markImportFailed(Long importId) {
        importRepository.findById(importId).ifPresent(imp -> {
            imp.setStatus(ImportStatus.FAILED.name());
            imp.setUpdatedAt(Instant.now());
            importRepository.save(imp);
        });
    }
}
