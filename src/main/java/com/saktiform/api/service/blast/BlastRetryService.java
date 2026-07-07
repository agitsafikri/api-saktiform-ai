package com.saktiform.api.service.blast;

import com.saktiform.api.entity.BlastCampaign;
import com.saktiform.api.entity.BlastJob;
import com.saktiform.api.entity.BlastMessage;
import com.saktiform.api.entity.BlastMessageEvent;
import com.saktiform.api.model.blast.enums.CampaignStatus;
import com.saktiform.api.model.blast.enums.JobStatus;
import com.saktiform.api.model.blast.enums.MessageStatus;
import com.saktiform.api.repository.BlastCampaignRepository;
import com.saktiform.api.repository.BlastJobRepository;
import com.saktiform.api.repository.BlastMessageEventRepository;
import com.saktiform.api.repository.BlastMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Retry pesan FAILED: reset FAILED→WAITING (+retry_count), buat job baru dengan attempt unik & budget
 * baru; histori (event & job lama) dipertahankan (FR-10, BR-7).
 */
@Service
public class BlastRetryService {

    private final BlastCampaignRepository campaignRepository;
    private final BlastMessageRepository messageRepository;
    private final BlastJobRepository jobRepository;
    private final BlastMessageEventRepository eventRepository;

    @Value("${blast.worker.max-attempts:3}")
    private int defaultMaxAttempts;

    public BlastRetryService(BlastCampaignRepository campaignRepository,
                             BlastMessageRepository messageRepository,
                             BlastJobRepository jobRepository,
                             BlastMessageEventRepository eventRepository) {
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.jobRepository = jobRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public int retry(UUID campaignId, List<Long> messageIds, Long workspaceId) {
        BlastCampaign c = campaignRepository.findByIdAndIdWorkspace(campaignId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign tidak ditemukan"));
        if (CampaignStatus.CANCELLED.name().equals(c.getStatus())) {
            throw new IllegalStateException("Campaign CANCELLED tidak bisa di-retry");
        }

        List<BlastMessage> targets;
        if (messageIds != null && !messageIds.isEmpty()) {
            targets = messageRepository.findAllById(messageIds).stream()
                    .filter(m -> campaignId.equals(m.getCampaignId())
                            && MessageStatus.FAILED.name().equals(m.getStatus()))
                    .toList();
        } else {
            targets = messageRepository.findByCampaignIdAndStatus(campaignId, MessageStatus.FAILED.name());
        }

        int cfgMaxAttempts = c.getMaxAttempts() != null ? c.getMaxAttempts() : defaultMaxAttempts;
        Instant now = Instant.now();
        int count = 0;

        for (BlastMessage m : targets) {
            Integer maxAttempt = jobRepository.findMaxAttemptByMessageId(m.getId());
            int nextAttempt = (maxAttempt == null ? 0 : maxAttempt) + 1;

            appendEvent(m, MessageStatus.FAILED.name(), MessageStatus.WAITING.name(), "USER");
            m.setStatus(MessageStatus.WAITING.name());
            m.setRetryCount((m.getRetryCount() == null ? 0 : m.getRetryCount()) + 1);
            m.setWaitingAt(now);
            m.setLastError(null);
            m.setUpdatedAt(now);
            messageRepository.save(m);
            campaignRepository.onRetryFromFailed(campaignId);

            BlastJob job = new BlastJob();
            job.setIdWorkspace(m.getIdWorkspace());
            job.setCampaignId(campaignId);
            job.setMessageId(m.getId());
            job.setStatus(JobStatus.READY.name());
            job.setAttempt(nextAttempt);
            job.setMaxAttempts(nextAttempt + cfgMaxAttempts - 1); // siklus attempt baru
            job.setPriority((short) 0);
            job.setDedupKey(campaignId + ":" + m.getId() + ":" + nextAttempt);
            job.setAvailableAt(now);
            job.setCreatedAt(now);
            jobRepository.save(job);
            count++;
        }

        if (count > 0 && CampaignStatus.FINISHED.name().equals(c.getStatus())) {
            campaignRepository.onReopen(campaignId); // FINISHED → RUNNING agar worker melanjutkan
        }
        return count;
    }

    private void appendEvent(BlastMessage m, String from, String to, String source) {
        BlastMessageEvent e = new BlastMessageEvent();
        e.setMessageId(m.getId());
        e.setIdWorkspace(m.getIdWorkspace());
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setSource(source);
        e.setCreatedAt(Instant.now());
        eventRepository.save(e);
    }
}
