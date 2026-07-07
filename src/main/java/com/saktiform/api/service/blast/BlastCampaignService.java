package com.saktiform.api.service.blast;

import com.saktiform.api.entity.BlastCampaign;
import com.saktiform.api.entity.BlastImport;
import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.blast.enums.CampaignStatus;
import com.saktiform.api.model.blast.enums.ImportStatus;
import com.saktiform.api.model.blast.enums.MessageSource;
import com.saktiform.api.model.blast.enums.MessageStatus;
import com.saktiform.api.model.blast.enums.TargetType;
import com.saktiform.api.model.blast.event.BlastCampaignStartEvent;
import com.saktiform.api.model.blast.request.CreateCampaignRequest;
import com.saktiform.api.entity.BlastMessage;
import com.saktiform.api.model.blast.response.CampaignDetailDto;
import com.saktiform.api.model.blast.response.CampaignListProjection;
import com.saktiform.api.model.blast.response.CampaignProgressDto;
import com.saktiform.api.model.blast.response.CampaignResponse;
import com.saktiform.api.model.blast.response.MessageListDto;
import com.saktiform.api.model.blast.response.ReviewDto;
import com.saktiform.api.model.blast.response.StatusCountProjection;
import com.saktiform.api.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.saktiform.api.service.StorageService;
import com.saktiform.api.service.blast.placeholder.BlastMessageContext;
import com.saktiform.api.service.blast.placeholder.PlaceholderEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BlastCampaignService {

    private final BlastCampaignRepository campaignRepository;
    private final BlastImportRepository importRepository;
    private final BlastImportContactRepository contactRepository;
    private final BlastMessageRepository messageRepository;
    private final BlastJobRepository jobRepository;
    private final ChatTemplateRepository chatTemplateRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PlaceholderEngine placeholderEngine;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${blast.worker.delay-ms:1500}")
    private int defaultDelayMs;

    @Value("${blast.worker.max-attempts:3}")
    private int defaultMaxAttempts;

    public BlastCampaignService(BlastCampaignRepository campaignRepository,
                                BlastImportRepository importRepository,
                                BlastImportContactRepository contactRepository,
                                BlastMessageRepository messageRepository,
                                BlastJobRepository jobRepository,
                                ChatTemplateRepository chatTemplateRepository,
                                WorkspaceRepository workspaceRepository,
                                PlaceholderEngine placeholderEngine,
                                StorageService storageService,
                                ApplicationEventPublisher eventPublisher) {
        this.campaignRepository = campaignRepository;
        this.importRepository = importRepository;
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
        this.jobRepository = jobRepository;
        this.chatTemplateRepository = chatTemplateRepository;
        this.workspaceRepository = workspaceRepository;
        this.placeholderEngine = placeholderEngine;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CampaignResponse create(CreateCampaignRequest req, Long workspaceId, Long accountId) {
        BlastImport imp = importRepository.findByIdAndIdWorkspace(req.getImportId(), workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Import tidak ditemukan"));
        if (ImportStatus.CONSUMED.name().equals(imp.getStatus())) {
            throw new IllegalStateException("Import sudah dipakai untuk campaign lain (1 import = 1 campaign)");
        }
        if (!ImportStatus.ANALYZED.name().equals(imp.getStatus())) {
            throw new IllegalStateException("Import belum dianalisis (status " + imp.getStatus() + ")");
        }

        String content;
        String mediaLink;
        UUID sourceTemplateId = null;
        MessageSource source = req.getMessageSource();
        if (source == MessageSource.TEMPLATE) {
            if (req.getTemplateId() == null) {
                throw new IllegalArgumentException("templateId wajib untuk messageSource TEMPLATE");
            }
            ChatTemplate tpl = chatTemplateRepository.findById(req.getTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("Template tidak ditemukan"));
            if (!workspaceId.equals(tpl.getIdWorkspace())) {
                throw new IllegalArgumentException("Template bukan milik workspace ini");
            }
            content = tpl.getContent();
            mediaLink = tpl.getMediaLink();
            sourceTemplateId = tpl.getId();
        } else {
            if (req.getContent() == null || req.getContent().isBlank()) {
                throw new IllegalArgumentException("content wajib untuk messageSource CUSTOM");
            }
            content = req.getContent();
            mediaLink = req.getMediaLink();
        }

        TargetType target = req.getTargetType();
        int projected = (int) contactRepository.countByImportIdAndCategoryIn(req.getImportId(), targetCategories(target));
        if (projected < 1) {
            throw new IllegalStateException("Tidak ada recipient valid untuk target " + target);
        }

        String deviceId = req.getDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            Workspace ws = workspaceRepository.findById(workspaceId).orElse(null);
            if (ws != null && ws.getWabaId() != null) {
                deviceId = ws.getWabaId().toString();
            }
        }

        Instant now = Instant.now();
        BlastCampaign c = new BlastCampaign();
        c.setIdWorkspace(workspaceId);
        c.setImportId(imp.getId());
        c.setCreatedBy(accountId);
        c.setName(req.getName());
        c.setStatus(CampaignStatus.DRAFT.name());
        c.setMessageSource(source.name());
        c.setSourceTemplateId(sourceTemplateId);
        c.setMessageContent(content);
        c.setMediaLink(mediaLink);
        c.setTargetType(target.name());
        c.setDeviceId(deviceId);
        if (req.getConfig() != null) {
            c.setBatchSize(req.getConfig().getBatchSize());
            c.setDelayMs(req.getConfig().getDelayMs());
            c.setMaxAttempts(req.getConfig().getMaxAttempts());
        }
        c.setTotalRecipient(projected);
        c.setCountWaiting(0);
        c.setCreatedAt(now);
        c = campaignRepository.save(c);

        // 1 import = 1 campaign (BR-22). @Version pada import mencegah race dua create paralel.
        imp.setStatus(ImportStatus.CONSUMED.name());
        imp.setUpdatedAt(now);
        importRepository.save(imp);

        return new CampaignResponse(c.getId(), c.getStatus(), projected);
    }

    @Transactional(readOnly = true)
    public ReviewDto review(UUID campaignId, Long workspaceId) {
        BlastCampaign c = campaignRepository.findByIdAndIdWorkspace(campaignId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign tidak ditemukan"));

        String sampleName = "Customer";
        String samplePhone = "62800000000";
        if (c.getImportId() != null && c.getTargetType() != null) {
            var sample = contactRepository.findFirstByImportIdAndCategoryInOrderByIdAsc(
                    c.getImportId(), targetCategories(TargetType.valueOf(c.getTargetType())));
            if (sample.isPresent()) {
                if (sample.get().getRawName() != null && !sample.get().getRawName().isBlank()) {
                    sampleName = sample.get().getRawName();
                }
                if (sample.get().getNormalizedPhone() != null) {
                    samplePhone = sample.get().getNormalizedPhone();
                }
            }
        }
        String preview = placeholderEngine.render(c.getMessageContent(), new BlastMessageContext(sampleName, samplePhone));
        int delay = c.getDelayMs() != null ? c.getDelayMs() : defaultDelayMs;
        long estimate = (long) (c.getTotalRecipient() == null ? 0 : c.getTotalRecipient()) * delay / 1000L;
        return new ReviewDto(c.getName(), c.getTotalRecipient(), preview, estimate, c.getStatus());
    }

    @Transactional
    public CampaignResponse start(UUID campaignId, Long workspaceId) {
        BlastCampaign c = campaignRepository.findByIdAndIdWorkspace(campaignId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign tidak ditemukan"));
        if (!CampaignStatus.DRAFT.name().equals(c.getStatus())) {
            throw new IllegalStateException("Hanya campaign DRAFT yang bisa di-start (status: " + c.getStatus() + ")");
        }
        if (c.getTotalRecipient() == null || c.getTotalRecipient() < 1) {
            throw new IllegalStateException("Campaign tidak memiliki recipient");
        }
        c.setStatus(CampaignStatus.QUEUED.name());
        c.setUpdatedAt(Instant.now());
        campaignRepository.save(c); // @Version mencegah double-start (BR-13)

        eventPublisher.publishEvent(new BlastCampaignStartEvent(c.getId()));
        return new CampaignResponse(c.getId(), c.getStatus(), c.getTotalRecipient());
    }

    /** Generate recipient + queue. Dipanggil {@code BlastCampaignStartListener} (cross-bean → tx sendiri). */
    @Transactional
    public void generateRecipientsAndQueue(UUID campaignId) {
        BlastCampaign c = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign tidak ditemukan"));
        if (!CampaignStatus.QUEUED.name().equals(c.getStatus())) {
            return; // idempotent guard
        }
        int maxAttempts = c.getMaxAttempts() != null ? c.getMaxAttempts() : defaultMaxAttempts;
        messageRepository.generateRecipients(c.getId(), c.getImportId(), targetCategories(TargetType.valueOf(c.getTargetType())));
        jobRepository.generateQueue(c.getId(), maxAttempts);

        long total = messageRepository.countByCampaignId(c.getId());
        c.setTotalRecipient((int) total);
        c.setCountWaiting((int) total);
        c.setCountSending(0);
        c.setCountSent(0);
        c.setCountFailed(0);
        c.setCountReplied(0);
        c.setCountSkipped(0);
        c.setUpdatedAt(Instant.now());
        campaignRepository.save(c);
    }

    @Transactional
    public CampaignResponse pause(UUID campaignId, Long workspaceId) {
        BlastCampaign c = load(campaignId, workspaceId);
        if (!CampaignStatus.RUNNING.name().equals(c.getStatus())) {
            throw new IllegalStateException("Pause hanya dari RUNNING (status: " + c.getStatus() + ")");
        }
        c.setStatus(CampaignStatus.PAUSED.name());
        c.setUpdatedAt(Instant.now());
        campaignRepository.save(c);
        return new CampaignResponse(c.getId(), c.getStatus(), c.getTotalRecipient());
    }

    @Transactional
    public CampaignResponse resume(UUID campaignId, Long workspaceId) {
        BlastCampaign c = load(campaignId, workspaceId);
        if (!CampaignStatus.PAUSED.name().equals(c.getStatus())) {
            throw new IllegalStateException("Resume hanya dari PAUSED (status: " + c.getStatus() + ")");
        }
        c.setStatus(CampaignStatus.RUNNING.name());
        c.setUpdatedAt(Instant.now());
        campaignRepository.save(c);
        return new CampaignResponse(c.getId(), c.getStatus(), c.getTotalRecipient());
    }

    @Transactional
    public CampaignResponse cancel(UUID campaignId, Long workspaceId) {
        BlastCampaign c = load(campaignId, workspaceId);
        String s = c.getStatus();
        if (CampaignStatus.CANCELLED.name().equals(s) || CampaignStatus.FINISHED.name().equals(s)
                || CampaignStatus.FAILED.name().equals(s)) {
            throw new IllegalStateException("Campaign sudah terminal, tidak bisa di-cancel (status: " + s + ")");
        }
        jobRepository.cancelPending(c.getId());
        messageRepository.markWaitingSkipped(c.getId());
        recomputeCounters(c);
        c.setStatus(CampaignStatus.CANCELLED.name());
        c.setFinishedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        campaignRepository.save(c);
        return new CampaignResponse(c.getId(), c.getStatus(), c.getTotalRecipient());
    }

    // ---- monitoring & history (read) ----

    @Transactional(readOnly = true)
    public Page<CampaignListProjection> list(Long workspaceId, String search, String status, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), limit);
        String s = (status == null || status.isBlank()) ? null : status;
        String kw = (search == null || search.isBlank()) ? null : search;
        return campaignRepository.search(workspaceId, s, kw, pageable);
    }

    @Transactional(readOnly = true)
    public CampaignDetailDto detail(UUID campaignId, Long workspaceId) {
        CampaignDetailDto dto = CampaignDetailDto.from(load(campaignId, workspaceId));
        dto.setMediaLink(publicUrl(dto.getMediaLink()));
        return dto;
    }

    @Transactional(readOnly = true)
    public CampaignProgressDto progress(UUID campaignId, Long workspaceId) {
        return CampaignProgressDto.from(load(campaignId, workspaceId));
    }

    @Transactional(readOnly = true)
    public Page<MessageListDto> messages(UUID campaignId, Long workspaceId, String status, int page, int limit) {
        load(campaignId, workspaceId); // validasi kepemilikan workspace
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), limit);
        Page<BlastMessage> pg = (status == null || status.isBlank())
                ? messageRepository.findByCampaignId(campaignId, pageable)
                : messageRepository.findByCampaignIdAndStatus(campaignId, status, pageable);
        return pg.map(MessageListDto::from);
    }

    /** Rekonsiliasi counter seluruh campaign RUNNING (dipanggil BlastCounterReconciler, OQ-9). */
    @Transactional
    public void reconcileRunning() {
        for (UUID id : campaignRepository.findRunningIds()) {
            campaignRepository.findById(id).ifPresent(c -> {
                recomputeCounters(c);
                c.setUpdatedAt(Instant.now());
                campaignRepository.save(c);
            });
        }
    }

    // ---- helpers ----

    private BlastCampaign load(UUID campaignId, Long workspaceId) {
        return campaignRepository.findByIdAndIdWorkspace(campaignId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign tidak ditemukan"));
    }

    /** Path storage → URL publik; null/kosong tetap null (jika tak ada media); URL http(s) dibiarkan apa adanya. */
    private String publicUrl(String mediaLink) {
        if (mediaLink == null || mediaLink.isBlank()) return null;
        String lower = mediaLink.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) return mediaLink;
        return storageService.getPublicUrl(mediaLink);
    }

    private List<String> targetCategories(TargetType target) {
        return switch (target) {
            case ALL_VALID -> List.of("EXISTING", "NEW");
            case EXISTING_ONLY -> List.of("EXISTING");
            case NEW_ONLY -> List.of("NEW");
        };
    }

    /** Hitung ulang counter campaign dari blast_message (dipakai cancel; reuse oleh reconciler Fase 6). */
    void recomputeCounters(BlastCampaign c) {
        Map<String, Long> m = new HashMap<>();
        for (StatusCountProjection p : messageRepository.countByStatus(c.getId())) {
            m.put(p.getStatus(), p.getCnt());
        }
        long waiting = count(m, MessageStatus.WAITING);
        long sending = count(m, MessageStatus.SENDING);
        long sent = count(m, MessageStatus.SENT) + count(m, MessageStatus.DELIVERED)
                + count(m, MessageStatus.READ) + count(m, MessageStatus.REPLIED);
        long failed = count(m, MessageStatus.FAILED);
        long replied = count(m, MessageStatus.REPLIED);
        long skipped = count(m, MessageStatus.SKIPPED);
        c.setCountWaiting((int) waiting);
        c.setCountSending((int) sending);
        c.setCountSent((int) sent);
        c.setCountFailed((int) failed);
        c.setCountReplied((int) replied);
        c.setCountSkipped((int) skipped);
        c.setTotalRecipient((int) (waiting + sending + sent + failed + skipped));
    }

    private long count(Map<String, Long> m, MessageStatus status) {
        return m.getOrDefault(status.name(), 0L);
    }
}
