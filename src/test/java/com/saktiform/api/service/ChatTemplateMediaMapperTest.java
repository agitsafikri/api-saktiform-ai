package com.saktiform.api.service;

import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.model.template.AddChatTemplateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatTemplateMediaMapperTest {

    private StorageService storageService;
    private ChatTemplateMediaMapper mapper;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        mapper = new ChatTemplateMediaMapper(storageService);
        ReflectionTestUtils.setField(mapper, "maxImages", 10);

        when(storageService.extractPathFromPublicUrl(anyString()))
                .thenAnswer(inv -> "path/" + inv.<String>getArgument(0));
        when(storageService.getProdukPublicUrl(anyString()))
                .thenAnswer(inv -> "https://cdn/produk-media/" + inv.<String>getArgument(0));
    }

    private static AddChatTemplateDto request(String mediaLink, String... mediaLinks) {
        var dto = new AddChatTemplateDto();
        dto.setMediaLink(mediaLink);
        dto.setMediaLinks(mediaLinks == null ? null : new ArrayList<>(Arrays.asList(mediaLinks)));
        return dto;
    }

    @Test
    void mediaLinksKeepOrder() {
        assertEquals(List.of("path/a", "path/b", "path/c"), mapper.toPaths(request(null, "a", "b", "c")));
    }

    @Test
    void fallsBackToLegacyMediaLink() {
        assertEquals(List.of("path/x"), mapper.toPaths(request("x", (String[]) null)));
    }

    @Test
    void noMediaGivesEmptyList() {
        assertEquals(List.of(), mapper.toPaths(request(null, (String[]) null)));
    }

    @Test
    void mediaLinksTakesPriorityOverMediaLink() {
        assertEquals(List.of("path/a"), mapper.toPaths(request("x", "a")));
    }

    @Test
    void emptyMediaLinksClearsEvenWhenLegacyFieldSent() {
        assertEquals(List.of(), mapper.toPaths(request("x")));
    }

    @Test
    void blankAndNullEntriesAreDropped() {
        assertEquals(List.of("path/a", "path/b"), mapper.toPaths(request(null, "a", "", null, " ", "b")));
    }

    @Test
    void rejectsMoreThanMaxBeforeExtracting() {
        var links = Collections.nCopies(11, "u").toArray(String[]::new);

        var ex = assertThrows(IllegalArgumentException.class, () -> mapper.toPaths(request(null, links)));
        assertEquals("Maksimal 10 gambar per template", ex.getMessage());
        verify(storageService, never()).extractPathFromPublicUrl(anyString());
    }

    @Test
    void invalidUrlPropagates() {
        when(storageService.extractPathFromPublicUrl("bad"))
                .thenThrow(new IllegalArgumentException("URL tidak valid untuk bucket ini"));

        var ex = assertThrows(IllegalArgumentException.class, () -> mapper.toPaths(request(null, "a", "bad")));
        assertEquals("URL tidak valid untuk bucket ini", ex.getMessage());
    }

    @Test
    void duplicatesAreKept() {
        assertEquals(List.of("path/a", "path/a"), mapper.toPaths(request(null, "a", "a")));
    }

    @Test
    void publicUrlsPreferMediaLinks() {
        var template = new ChatTemplate();
        template.setMediaLink("p1");
        template.setMediaLinks(List.of("p1", "p2"));

        assertEquals(List.of("https://cdn/produk-media/p1", "https://cdn/produk-media/p2"),
                mapper.toPublicUrls(template));
    }

    @Test
    void publicUrlsFallBackToLegacyMediaLink() {
        var template = new ChatTemplate();
        template.setMediaLink("p1");

        assertEquals(List.of("https://cdn/produk-media/p1"), mapper.toPublicUrls(template));
    }

    @Test
    void publicUrlsEmptyWhenNoMedia() {
        var template = new ChatTemplate();
        template.setMediaLinks(List.of());

        assertEquals(List.of(), mapper.toPublicUrls(template));
    }
}
