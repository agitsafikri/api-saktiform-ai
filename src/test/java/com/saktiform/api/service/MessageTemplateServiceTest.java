package com.saktiform.api.service;

import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.model.template.AddChatTemplateDto;
import com.saktiform.api.repository.ChatTemplateRepository;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageTemplateServiceTest {

    private ChatTemplateRepository repository;
    private ChatTemplateMediaMapper mapper;
    private MessageTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(ChatTemplateRepository.class);
        mapper = mock(ChatTemplateMediaMapper.class);
        service = new MessageTemplateService(repository, mock(MessageConstructorHelper.class), mapper);
    }

    private ChatTemplate saved() {
        ArgumentCaptor<ChatTemplate> captor = ArgumentCaptor.forClass(ChatTemplate.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createStoresAllPathsAndFirstAsMediaLink() {
        var dto = new AddChatTemplateDto();
        dto.setIdWorkspace(1L);
        dto.setNamaTemplate("Katalog");
        when(mapper.toPaths(dto)).thenReturn(List.of("p1", "p2", "p3"));

        service.upsertMessageTemplate(dto);

        var template = saved();
        assertEquals(List.of("p1", "p2", "p3"), template.getMediaLinks());
        assertEquals("p1", template.getMediaLink());
        assertEquals("QUICK_REPLY", template.getCategory());
    }

    @Test
    void updateWithNoImagesClearsBothColumns() {
        var id = UUID.randomUUID();
        var existing = new ChatTemplate();
        existing.setId(id);
        existing.setMediaLink("old");
        existing.setMediaLinks(List.of("old", "old2"));
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        var dto = new AddChatTemplateDto();
        dto.setId(id);
        when(mapper.toPaths(dto)).thenReturn(List.of());

        service.upsertMessageTemplate(dto);

        var template = saved();
        assertNull(template.getMediaLinks());
        assertNull(template.getMediaLink());
    }

    @Test
    void invalidMediaDoesNotTouchTemplate() {
        var dto = new AddChatTemplateDto();
        dto.setId(UUID.randomUUID());
        when(mapper.toPaths(dto)).thenThrow(new IllegalArgumentException("URL tidak valid untuk bucket ini"));

        assertThrows(IllegalArgumentException.class, () -> service.upsertMessageTemplate(dto));
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void detailReturnsAllUrlsAndFirstAsMediaLink() {
        var id = UUID.randomUUID();
        var template = new ChatTemplate();
        template.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(template));
        when(mapper.toPublicUrls(template)).thenReturn(List.of("u1", "u2"));

        var detail = service.getDetail(id);

        assertEquals(List.of("u1", "u2"), detail.getMediaLinks());
        assertEquals("u1", detail.getMediaLink());
    }

    @Test
    void detailWithoutImages() {
        var id = UUID.randomUUID();
        var template = new ChatTemplate();
        when(repository.findById(id)).thenReturn(Optional.of(template));
        when(mapper.toPublicUrls(template)).thenReturn(List.of());

        var detail = service.getDetail(id);

        assertEquals(List.of(), detail.getMediaLinks());
        assertNull(detail.getMediaLink());
    }

    @Test
    void listMapsEntitiesAndKeepsPaging() {
        var template = new ChatTemplate();
        template.setId(UUID.randomUUID());
        template.setNamaTemplate("Katalog");
        template.setCategory("QUICK_REPLY");
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findListChatTemplate(eq(1L), any(), eq("")))
                .thenReturn(new PageImpl<>(List.of(template), pageable, 25));
        when(mapper.toPublicUrls(template)).thenReturn(List.of("u1", "u2"));

        var page = service.getListTemplate(1L, 10, 1, "");

        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        var item = page.getContent().get(0);
        assertEquals("Katalog", item.getNamaTemplate());
        assertEquals("QUICK_REPLY", item.getKategori());
        assertEquals("u1", item.getMediaLink());
        assertEquals(List.of("u1", "u2"), item.getMediaLinks());
    }
}
