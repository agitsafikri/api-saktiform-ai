package com.saktiform.api.service.label;

import com.saktiform.api.entity.Conversation;
import com.saktiform.api.entity.ConversationLabel;
import com.saktiform.api.entity.ConversationLabelLink;
import com.saktiform.api.model.label.response.ConversationLabelProjection;
import com.saktiform.api.model.label.response.LabelDto;
import com.saktiform.api.repository.ConversationLabelLinkRepository;
import com.saktiform.api.repository.ConversationLabelRepository;
import com.saktiform.api.repository.ConversationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Layanan master label (CRUD) + assignment ke conversation. Semua operasi terisolasi per workspace.
 * Assignment ditambahkan pada fase berikutnya.
 */
@Service
public class ConversationLabelService {

    private static final String DUPLICATE_MESSAGE = "Label dengan nama tersebut sudah ada di workspace ini";

    private final ConversationLabelRepository labelRepository;
    private final ConversationLabelLinkRepository linkRepository;
    private final ConversationRepository conversationRepository;

    public ConversationLabelService(ConversationLabelRepository labelRepository,
                                    ConversationLabelLinkRepository linkRepository,
                                    ConversationRepository conversationRepository) {
        this.labelRepository = labelRepository;
        this.linkRepository = linkRepository;
        this.conversationRepository = conversationRepository;
    }

    // ---- master label ----

    @Transactional
    public LabelDto create(com.saktiform.api.model.label.request.LabelRequest req, Long workspaceId) {
        String name = req.getName().trim();
        String color = HexColor.normalize(req.getColorHex());
        if (labelRepository.existsByWorkspaceAndName(workspaceId, name, null)) {
            throw new IllegalStateException(DUPLICATE_MESSAGE);
        }
        Instant now = Instant.now();
        ConversationLabel l = new ConversationLabel();
        l.setIdWorkspace(workspaceId);
        l.setName(name);
        l.setColorHex(color);
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        try {
            return LabelDto.from(labelRepository.save(l));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(DUPLICATE_MESSAGE); // backstop unique index (race)
        }
    }

    @Transactional(readOnly = true)
    public List<LabelDto> list(Long workspaceId) {
        return labelRepository.findByIdWorkspaceOrderByNameAsc(workspaceId)
                .stream().map(LabelDto::from).toList();
    }

    @Transactional
    public LabelDto update(Long labelId, com.saktiform.api.model.label.request.LabelRequest req, Long workspaceId) {
        ConversationLabel l = labelRepository.findByIdAndIdWorkspace(labelId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Label tidak ditemukan"));
        String name = req.getName().trim();
        String color = HexColor.normalize(req.getColorHex());
        if (labelRepository.existsByWorkspaceAndName(workspaceId, name, labelId)) {
            throw new IllegalStateException(DUPLICATE_MESSAGE);
        }
        l.setName(name);
        l.setColorHex(color);
        l.setUpdatedAt(Instant.now());
        try {
            return LabelDto.from(labelRepository.save(l));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(DUPLICATE_MESSAGE); // backstop unique index (race)
        }
    }

    @Transactional
    public void delete(Long labelId, Long workspaceId) {
        ConversationLabel l = labelRepository.findByIdAndIdWorkspace(labelId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Label tidak ditemukan"));
        linkRepository.deleteByLabelId(l.getId());   // cascade di service (FR-6)
        labelRepository.delete(l);
    }

    // ---- assignment ----

    /** Assign satu/banyak label ke conversation. All-or-nothing (FR-11) + idempotent (FR-10). */
    @Transactional
    public List<LabelDto> assign(UUID conversationId, List<Long> labelIds, Long workspaceId) {
        requireConversationInWorkspace(conversationId, workspaceId);
        List<Long> ids = labelIds.stream().distinct().toList();

        // all-or-nothing: seluruh id harus ada & milik workspace ini
        List<ConversationLabel> labels = labelRepository.findByIdWorkspaceAndIdIn(workspaceId, ids);
        if (labels.size() != ids.size()) {
            throw new IllegalArgumentException("Sebagian labelId tidak ditemukan / bukan milik workspace ini");
        }
        Instant now = Instant.now();
        for (ConversationLabel l : labels) {
            if (linkRepository.existsByConversationIdAndLabelId(conversationId, l.getId())) {
                continue; // idempotent — sudah terpasang
            }
            ConversationLabelLink link = new ConversationLabelLink();
            link.setConversationId(conversationId);
            link.setLabelId(l.getId());
            link.setIdWorkspace(workspaceId);
            link.setCreatedAt(now);
            linkRepository.save(link);
        }
        return listForConversation(conversationId, workspaceId);
    }

    /** Unassign sebuah label dari conversation. Idempotent — 200 no-op bila tidak terpasang (FR-12). */
    @Transactional
    public void unassign(UUID conversationId, Long labelId, Long workspaceId) {
        requireConversationInWorkspace(conversationId, workspaceId);
        linkRepository.deleteByConversationIdAndLabelId(conversationId, labelId);
    }

    /** Daftar label yang terpasang pada sebuah conversation. */
    @Transactional(readOnly = true)
    public List<LabelDto> listForConversation(UUID conversationId, Long workspaceId) {
        requireConversationInWorkspace(conversationId, workspaceId);
        return linkRepository.findLabelsByConversationId(conversationId)
                .stream().map(LabelDto::from).toList();
    }

    /** Batch fetch label per conversation (anti N+1) — dipakai integrasi list conversation. */
    @Transactional(readOnly = true)
    public Map<UUID, List<LabelDto>> labelsByConversationIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<LabelDto>> map = new HashMap<>();
        for (ConversationLabelProjection p : linkRepository.findLabelsByConversationIds(ids)) {
            map.computeIfAbsent(p.getConversationId(), k -> new ArrayList<>()).add(LabelDto.from(p));
        }
        return map;
    }

    // ---- helper ----

    /** Validasi conversation ada & milik workspace (via contact.id_workspace). */
    private void requireConversationInWorkspace(UUID conversationId, Long workspaceId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation tidak ditemukan"));
        Long ws = c.getContact() != null ? c.getContact().getIdWorkspace() : null;
        if (ws == null || !ws.equals(workspaceId)) {
            throw new IllegalArgumentException("Conversation bukan milik workspace ini");
        }
    }
}
