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
import com.saktiform.api.service.StorageService;
import com.saktiform.api.service.blast.placeholder.BlastMessageContext;
import com.saktiform.api.service.blast.placeholder.PlaceholderEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Operasi transaksional singkat untuk satu job pengiriman. Dipisah dari {@link BlastSenderService}
 * agar pemanggilan WhatsApp API + sleep delay berada DI LUAR transaksi (tidak menahan koneksi Hikari).
 *
 * Counter selalu seimbang: WAITING→SENDING (onSending), SENDING→SENT (onSent),
 * SENDING→FAILED (onFailed), SENDING→WAITING (onRequeue).
 */
@Service
public class BlastSendTxService {

    private final BlastJobRepository jobRepository;
    private final BlastMessageRepository messageRepository;
    private final BlastCampaignRepository campaignRepository;
    private final BlastMessageEventRepository eventRepository;
    private final PlaceholderEngine placeholderEngine;
    private final StorageService storageService;
    private final BlastConversationService blastConversationService;

    private static final Logger log = LoggerFactory.getLogger(BlastSendTxService.class);

    @Value("${blast.worker.delay-ms:1500}")
    private int defaultDelayMs;

    @Value("${blast.worker.backoff-base-ms:30000}")
    private long backoffBaseMs;

    public BlastSendTxService(BlastJobRepository jobRepository,
                              BlastMessageRepository messageRepository,
                              BlastCampaignRepository campaignRepository,
                              BlastMessageEventRepository eventRepository,
                              PlaceholderEngine placeholderEngine,
                              StorageService storageService,
                              BlastConversationService blastConversationService) {
        this.jobRepository = jobRepository;
        this.messageRepository = messageRepository;
        this.campaignRepository = campaignRepository;
        this.eventRepository = eventRepository;
        this.placeholderEngine = placeholderEngine;
        this.storageService = storageService;
        this.blastConversationService = blastConversationService;
    }

    /**
     * Transaksi 1: validasi + guard idempotency + transisi WAITING→SENDING + render.
     * @return SendContext bila harus dikirim; null bila di-skip (sudah terminal / interrupted / dll).
     */
    @Transactional
    public SendContext beginSend(Long jobId) {
        BlastJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !JobStatus.CLAIMED.name().equals(job.getStatus())) {
            return null; // sudah diproses worker lain / status berubah
        }
        BlastMessage msg = messageRepository.findById(job.getMessageId()).orElse(null);
        if (msg == null) {
            failJob(job, "blast_message tidak ditemukan");
            return null;
        }
        BlastCampaign campaign = campaignRepository.findById(job.getCampaignId()).orElse(null);
        if (campaign == null) {
            failJob(job, "campaign tidak ditemukan");
            return null;
        }

        Instant now = Instant.now();

        // HARDENING: campaign dapat berubah antara klaim (saat RUNNING) dan proses (jeda async).
        String campaignStatus = campaign.getStatus();
        if (CampaignStatus.CANCELLED.name().equals(campaignStatus)) {
            if (MessageStatus.WAITING.name().equals(msg.getStatus())) {
                appendEvent(msg, msg.getStatus(), MessageStatus.SKIPPED.name(), "SYSTEM", job.getAttempt(), "campaign cancelled");
                msg.setStatus(MessageStatus.SKIPPED.name());
                msg.setSkippedAt(now);
                msg.setUpdatedAt(now);
                messageRepository.save(msg);
                campaignRepository.onSkipped(campaign.getId());
            }
            job.setStatus(JobStatus.CANCELLED.name());
            job.setUpdatedAt(now);
            jobRepository.save(job);
            campaignRepository.markFinishedIfDone(campaign.getId());
            return null;
        }
        if (CampaignStatus.PAUSED.name().equals(campaignStatus)) {
            // lepas klaim → kembali READY; diklaim ulang setelah Resume (poller hanya klaim campaign RUNNING)
            job.setStatus(JobStatus.READY.name());
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setUpdatedAt(now);
            jobRepository.save(job);
            return null;
        }
        if (!CampaignStatus.RUNNING.name().equals(campaignStatus) && !CampaignStatus.QUEUED.name().equals(campaignStatus)) {
            job.setStatus(JobStatus.DONE.name()); // FINISHED/FAILED/DRAFT → jangan kirim
            job.setUpdatedAt(now);
            jobRepository.save(job);
            return null;
        }

        String status = msg.getStatus();

        if (!MessageStatus.WAITING.name().equals(status)) {
            if (MessageStatus.SENDING.name().equals(status)) {
                // Reclaim setelah lease expiry saat sebelumnya SENDING → tandai FAILED agar tidak nyangkut
                // & campaign tetap progres (counter: onFailed menyeimbangkan onSending sebelumnya).
                appendEvent(msg, status, MessageStatus.FAILED.name(), "WORKER", job.getAttempt(),
                        "interrupted: reclaimed while SENDING");
                msg.setStatus(MessageStatus.FAILED.name());
                msg.setFailedAt(now);
                msg.setLastError("interrupted while sending");
                msg.setUpdatedAt(now);
                messageRepository.save(msg);
                campaignRepository.onFailed(campaign.getId());
                job.setStatus(JobStatus.DEAD.name());
                job.setUpdatedAt(now);
                jobRepository.save(job);
                campaignRepository.markFinishedIfDone(campaign.getId());
            } else {
                // sudah terminal (SENT/.../SKIPPED) → ack tanpa kirim (idempotent)
                job.setStatus(JobStatus.DONE.name());
                job.setUpdatedAt(now);
                jobRepository.save(job);
            }
            return null;
        }

        // Flip campaign QUEUED → RUNNING saat job pertama diproses
        if (CampaignStatus.QUEUED.name().equals(campaign.getStatus())) {
            campaign.setStatus(CampaignStatus.RUNNING.name());
            campaign.setStartedAt(now);
            campaign.setUpdatedAt(now);
            campaignRepository.save(campaign);
        }

        // WAITING → SENDING
        appendEvent(msg, MessageStatus.WAITING.name(), MessageStatus.SENDING.name(), "WORKER", job.getAttempt(), null);
        msg.setStatus(MessageStatus.SENDING.name());
        msg.setSendingAt(now);
        msg.setUpdatedAt(now);
        messageRepository.save(msg);
        campaignRepository.onSending(campaign.getId());

        job.setStatus(JobStatus.PROCESSING.name());
        job.setUpdatedAt(now);
        jobRepository.save(job);

        String rendered = placeholderEngine.render(
                campaign.getMessageContent(),
                new BlastMessageContext(msg.getName(), msg.getPhone()));

        return SendContext.builder()
                .jobId(job.getId())
                .messageId(msg.getId())
                .campaignId(campaign.getId())
                .idWorkspace(msg.getIdWorkspace())
                .deviceId(campaign.getDeviceId())
                .phone(msg.getPhone())
                .renderedMessage(rendered)
                .mediaUrl(resolveMediaUrl(campaign.getMediaLink()))
                .mediaPath(resolveMediaPath(campaign.getMediaLink()))
                .createdBy(campaign.getCreatedBy())
                .attempt(job.getAttempt())
                .maxAttempts(job.getMaxAttempts())
                .delayMs(campaign.getDelayMs() != null ? campaign.getDelayMs() : defaultDelayMs)
                .build();
    }

    /** Transaksi 2a: pengiriman sukses → SENT + provider_message_id. */
    @Transactional
    public void completeSuccess(SendContext ctx, String providerMessageId) {
        BlastMessage msg = messageRepository.findById(ctx.getMessageId()).orElse(null);
        if (msg == null) return;
        Instant now = Instant.now();
        appendEvent(msg, msg.getStatus(), MessageStatus.SENT.name(), "WORKER", ctx.getAttempt(), null);
        msg.setStatus(MessageStatus.SENT.name());
        msg.setSentAt(now);
        msg.setProviderMessageId(providerMessageId);
        msg.setDeviceId(ctx.getDeviceId());
        msg.setRenderedMessage(ctx.getRenderedMessage());
        msg.setUpdatedAt(now);
        messageRepository.save(msg);

        campaignRepository.onSent(ctx.getCampaignId());

        jobRepository.findById(ctx.getJobId()).ifPresent(job -> {
            job.setStatus(JobStatus.DONE.name());
            job.setUpdatedAt(now);
            jobRepository.save(job);
        });

        // FASE 5: tempel ke Conversation (REQUIRES_NEW) — kegagalan attach tidak me-rollback status SENT.
        try {
            String type = ctx.getMediaUrl() != null ? "IMAGE" : "TEXT";
            BlastConversationService.OutboundResult result = blastConversationService.recordOutboundChat(
                    ctx.getIdWorkspace(), ctx.getPhone(), msg.getName(), providerMessageId,
                    type, ctx.getRenderedMessage(), ctx.getMediaPath(), ctx.getCreatedBy());
            msg.setConversationId(result.conversationId());
            msg.setChatId(result.chatId());
            msg.setUpdatedAt(Instant.now());
            messageRepository.save(msg);
        } catch (Exception e) {
            log.error("Attach conversation gagal untuk messageId={}: {}", ctx.getMessageId(), e.getMessage());
        }

        campaignRepository.markFinishedIfDone(ctx.getCampaignId());
    }

    /** Transaksi 2b: pengiriman gagal → retry (job baru) bila attempt masih ada, else FAILED. */
    @Transactional
    public void completeFailure(SendContext ctx, String error) {
        BlastMessage msg = messageRepository.findById(ctx.getMessageId()).orElse(null);
        if (msg == null) return;
        Instant now = Instant.now();
        BlastJob job = jobRepository.findById(ctx.getJobId()).orElse(null);
        String errTrim = error != null && error.length() > 500 ? error.substring(0, 500) : error;

        if (ctx.getAttempt() < ctx.getMaxAttempts()) {
            // Re-enqueue: SENDING → WAITING, buat job baru attempt+1 dengan backoff
            appendEvent(msg, msg.getStatus(), MessageStatus.WAITING.name(), "WORKER", ctx.getAttempt(), errTrim);
            msg.setStatus(MessageStatus.WAITING.name());
            msg.setWaitingAt(now);
            msg.setLastError(errTrim);
            msg.setUpdatedAt(now);
            messageRepository.save(msg);
            campaignRepository.onRequeue(ctx.getCampaignId());

            if (job != null) {
                job.setStatus(JobStatus.RETRYING.name());
                job.setLastError(errTrim);
                job.setUpdatedAt(now);
                jobRepository.save(job);
            }

            int nextAttempt = ctx.getAttempt() + 1;
            BlastJob next = new BlastJob();
            next.setIdWorkspace(ctx.getIdWorkspace());
            next.setCampaignId(ctx.getCampaignId());
            next.setMessageId(ctx.getMessageId());
            next.setStatus(JobStatus.READY.name());
            next.setAttempt(nextAttempt);
            next.setMaxAttempts(ctx.getMaxAttempts());
            next.setPriority((short) 0);
            next.setDedupKey(ctx.getCampaignId() + ":" + ctx.getMessageId() + ":" + nextAttempt);
            next.setAvailableAt(now.plusMillis(backoff(ctx.getAttempt())));
            next.setCreatedAt(now);
            jobRepository.save(next);
        } else {
            // Attempt habis → FAILED permanen
            appendEvent(msg, msg.getStatus(), MessageStatus.FAILED.name(), "WORKER", ctx.getAttempt(), errTrim);
            msg.setStatus(MessageStatus.FAILED.name());
            msg.setFailedAt(now);
            msg.setLastError(errTrim);
            msg.setUpdatedAt(now);
            messageRepository.save(msg);
            campaignRepository.onFailed(ctx.getCampaignId());

            if (job != null) {
                job.setStatus(JobStatus.DEAD.name());
                job.setLastError(errTrim);
                job.setUpdatedAt(now);
                jobRepository.save(job);
            }
            campaignRepository.markFinishedIfDone(ctx.getCampaignId());
        }
    }

    private void failJob(BlastJob job, String error) {
        job.setStatus(JobStatus.DEAD.name());
        job.setLastError(error);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
    }

    private long backoff(int attempt) {
        return backoffBaseMs * (1L << attempt); // exponential: base * 2^attempt
    }

    private String resolveMediaUrl(String mediaLink) {
        if (mediaLink == null || mediaLink.isBlank()) return null;
        String lower = mediaLink.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return mediaLink; // sudah URL publik
        }
        return storageService.getPublicUrl(mediaLink); // path storage → URL publik
    }

    /** Path storage untuk disimpan di Chat.media (kebalikan resolveMediaUrl). */
    private String resolveMediaPath(String mediaLink) {
        if (mediaLink == null || mediaLink.isBlank()) return null;
        String lower = mediaLink.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return storageService.extractPathFromPublicUrl(mediaLink);
        }
        return mediaLink; // sudah path
    }

    private void appendEvent(BlastMessage msg, String from, String to, String source, Integer attempt, String detail) {
        BlastMessageEvent e = new BlastMessageEvent();
        e.setMessageId(msg.getId());
        e.setIdWorkspace(msg.getIdWorkspace());
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setSource(source);
        e.setAttempt(attempt);
        e.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
        e.setCreatedAt(Instant.now());
        eventRepository.save(e);
    }
}
