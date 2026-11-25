package com.saktiform.api.service;

import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.model.template.AddChatTemplateDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Konversi media template: public URL request → path storage, dan path tersimpan → public URL
 * bucket produk. Dipakai bersama oleh {@link MessageTemplateService} dan ConversationService.
 */
@Component
public class ChatTemplateMediaMapper {
    private final StorageService storageService;

    @Value("${chat.template.max-images:10}")
    private int maxImages;

    public ChatTemplateMediaMapper(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * {@code mediaLinks} diprioritaskan; bila tidak dikirim, fallback ke {@code mediaLink} lama.
     * Seluruh URL divalidasi sebelum return sehingga tidak ada penyimpanan sebagian.
     */
    public List<String> toPaths(AddChatTemplateDto data) {
        List<String> raw = data.getMediaLinks() != null
                ? data.getMediaLinks()
                : (data.getMediaLink() != null ? List.of(data.getMediaLink()) : List.of());

        var urls = raw.stream().filter(u -> u != null && !u.isBlank()).toList();
        if (urls.size() > maxImages) {
            throw new IllegalArgumentException("Maksimal " + maxImages + " gambar per template");
        }
        return urls.stream().map(storageService::extractPathFromPublicUrl).toList();
    }

    /** Prioritas media_links, fallback media_link untuk data yang belum ter-backfill. */
    public List<String> toPublicUrls(ChatTemplate template) {
        List<String> paths = template.getMediaLinks() != null && !template.getMediaLinks().isEmpty()
                ? template.getMediaLinks()
                : (template.getMediaLink() != null && !template.getMediaLink().isBlank()
                        ? List.of(template.getMediaLink())
                        : List.of());
        return paths.stream().map(storageService::getProdukPublicUrl).toList();
    }
}
