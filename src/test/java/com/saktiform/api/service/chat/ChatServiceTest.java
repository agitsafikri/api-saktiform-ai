package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.chat.*;
import com.saktiform.api.model.whatsapp.WhatsappResponse;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.repository.ConversationRepository;
import com.saktiform.api.repository.OrderRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private static final String DEVICE = "device-1";
    private static final String PHONE = "628123";

    private WhatsappClientHelper client;
    private StorageService storageService;
    private ChatMessagePersister persister;
    private ChatMessageService chatMessageService;
    private ChatService service;

    private final UUID conversationId = UUID.randomUUID();
    private ChatSendContext ctx;

    @BeforeEach
    void setUp() {
        client = mock(WhatsappClientHelper.class);
        storageService = mock(StorageService.class);
        persister = mock(ChatMessagePersister.class);
        chatMessageService = mock(ChatMessageService.class);

        service = new ChatService(mock(WorkspaceRepository.class), mock(ApplicationEventPublisher.class),
                mock(AccountRepository.class), mock(ConversationRepository.class), mock(OrderRepository.class),
                storageService, chatMessageService, persister);
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "maxImages", 10);
        ReflectionTestUtils.setField(service, "delayMs", 0L);

        ctx = new ChatSendContext(conversationId, 1L, DEVICE, PHONE, "Budi");
        when(persister.loadContext(conversationId)).thenReturn(ctx);
        when(storageService.toChatBucketPath(anyString()))
                .thenAnswer(inv -> "path/" + inv.<String>getArgument(0));
        when(persister.saveSentChat(any(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(inv -> {
                    var chat = new Chat();
                    chat.setId(UUID.randomUUID());
                    chat.setMessageId(inv.getArgument(1));
                    chat.setType(inv.getArgument(2));
                    chat.setPesan(inv.getArgument(3));
                    chat.setMedia(inv.getArgument(4));
                    chat.setSentAt(Instant.now());
                    return chat;
                });
    }

    private static WhatsappResponse<SendResults> ok(String id) {
        var r = new WhatsappResponse<SendResults>();
        r.setCode("SUCCESS");
        r.setResults(new SendResults(id, "sent"));
        return r;
    }

    private static WhatsappResponse<SendResults> failed() {
        var r = new WhatsappResponse<SendResults>();
        r.setCode("INTERNAL_SERVER_ERROR");
        return r;
    }

    private SendMessageDto dto(ChatType type, String message, String... links) {
        var dto = new SendMessageDto();
        dto.setConversationId(conversationId);
        dto.setMessageType(type);
        dto.setMessage(message);
        dto.setMediaLink(links == null ? null : new ArrayList<>(Arrays.asList(links)));
        return dto;
    }

    @Test
    void sendsAllImagesInOrderWithCaptionOnFirstOnly() {
        when(client.sendImage(eq(DEVICE), eq(PHONE), anyString(), anyString()))
                .thenReturn(ok("m1"), ok("m2"), ok("m3"));

        var result = service.messageHandler(dto(ChatType.IMAGE, "caption", "u1", "u2", "u3"), "agent");

        InOrder order = inOrder(client);
        order.verify(client).sendImage(DEVICE, PHONE, "caption", "u1");
        order.verify(client).sendImage(DEVICE, PHONE, "", "u2");
        order.verify(client).sendImage(DEVICE, PHONE, "", "u3");

        verify(persister).saveSentChat(conversationId, "m1", "IMAGE", "caption", "path/u1", "agent");
        verify(persister).saveSentChat(conversationId, "m2", "IMAGE", null, "path/u2", "agent");
        verify(persister).saveSentChat(conversationId, "m3", "IMAGE", null, "path/u3", "agent");

        ArgumentCaptor<Chat> last = ArgumentCaptor.forClass(Chat.class);
        verify(persister, times(1)).updateConversationAfterSend(eq(ctx), last.capture(), eq("caption"));
        assertEquals("m3", last.getValue().getMessageId());

        assertEquals(3, result.getTotal());
        assertEquals(3, result.getSent());
        assertEquals(0, result.getFailed());
        assertEquals(3, result.getChatIds().size());
    }

    @Test
    void singleImageBehavesLikeBefore() {
        when(client.sendImage(any(), any(), any(), any())).thenReturn(ok("m1"));

        var result = service.messageHandler(dto(ChatType.IMAGE, "caption", "u1"), "agent");

        verify(client, times(1)).sendImage(DEVICE, PHONE, "caption", "u1");
        verify(persister, times(1)).saveSentChat(conversationId, "m1", "IMAGE", "caption", "path/u1", "agent");
        verify(persister, times(1)).updateConversationAfterSend(eq(ctx), any(), eq("caption"));
        assertEquals(1, result.getSent());
        assertEquals(0, result.getFailed());
    }

    @Test
    void rejectsMoreThanMaxImages() {
        var links = new String[11];
        Arrays.fill(links, "u");

        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.messageHandler(dto(ChatType.IMAGE, null, links), "agent"));
        assertEquals("Maksimal 10 gambar per pengiriman", ex.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void invalidUrlStopsBeforeAnySend() {
        when(storageService.toChatBucketPath("bad"))
                .thenThrow(new IllegalArgumentException("URL tidak valid untuk bucket ini"));

        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.messageHandler(dto(ChatType.IMAGE, null, "u1", "bad", "u3"), "agent"));
        assertEquals("URL tidak valid untuk bucket ini", ex.getMessage());
        verifyNoInteractions(client);
        verify(persister, never()).saveSentChat(any(), any(), any(), any(), any(), any());
    }

    @Test
    void templateImageCopyFailureStopsBeforeAnySend() {
        when(storageService.toChatBucketPath("tpl2"))
                .thenThrow(new RuntimeException("Gagal menyalin media template ke penyimpanan chat"));

        var ex = assertThrows(RuntimeException.class,
                () -> service.messageHandler(dto(ChatType.IMAGE, "caption", "tpl1", "tpl2"), "agent"));
        assertEquals("Gagal menyalin media template ke penyimpanan chat", ex.getMessage());
        verifyNoInteractions(client);
        verify(persister, never()).saveSentChat(any(), any(), any(), any(), any(), any());
    }

    @Test
    void imageWithoutMediaIsRejected() {
        for (var dto : List.of(dto(ChatType.IMAGE, null), dto(ChatType.IMAGE, null, (String[]) null),
                dto(ChatType.IMAGE, null, "", null))) {
            var ex = assertThrows(IllegalArgumentException.class, () -> service.messageHandler(dto, "agent"));
            assertEquals("Gambar wajib diisi", ex.getMessage());
        }
        verifyNoInteractions(client);
    }

    @Test
    void videoWithTwoFilesIsRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.messageHandler(dto(ChatType.VIDEO, null, "v1", "v2"), "agent"));
        assertEquals("Tipe VIDEO hanya mendukung 1 file", ex.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void partialFailureStopsAndKeepsSentImages() {
        when(client.sendImage(any(), any(), any(), any())).thenReturn(ok("m1"), failed());

        var result = service.messageHandler(dto(ChatType.IMAGE, "caption", "u1", "u2", "u3"), "agent");

        verify(client, times(2)).sendImage(any(), any(), any(), any());
        verify(client, never()).sendImage(any(), any(), any(), eq("u3"));
        verify(persister, times(1)).saveSentChat(any(), any(), any(), any(), any(), any());
        verify(persister, times(1)).updateConversationAfterSend(eq(ctx), any(), eq("caption"));
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getSent());
        assertEquals(2, result.getFailed());
    }

    @Test
    void failureOnFirstMessageThrowsExistingError() {
        when(client.sendImage(any(), any(), any(), any())).thenThrow(new RuntimeException("timeout"));

        var ex = assertThrows(RuntimeException.class,
                () -> service.messageHandler(dto(ChatType.IMAGE, "caption", "u1", "u2"), "agent"));
        assertEquals("Terjadi kesalahan saat mengirim pesan ke whatsapp, silahkan coba lagi.", ex.getMessage());
        verify(persister, never()).saveSentChat(any(), any(), any(), any(), any(), any());
        verify(persister, never()).updateConversationAfterSend(any(), any(), any());
    }

    @Test
    void textWithReplyUsesProviderMessageIdAndIgnoresMedia() {
        var repliedId = UUID.randomUUID();
        var replied = new Chat();
        replied.setMessageId("wa-replied");
        when(chatMessageService.findById(repliedId)).thenReturn(replied);
        when(client.sendMessage(eq(DEVICE), any())).thenReturn(ok("m1"));

        var dto = dto(ChatType.TEXT, "halo", "ignored");
        dto.setRepliedMessageId(repliedId);
        var result = service.messageHandler(dto, "agent");

        ArgumentCaptor<GoWaSendMessageRequest> req = ArgumentCaptor.forClass(GoWaSendMessageRequest.class);
        verify(client).sendMessage(eq(DEVICE), req.capture());
        assertEquals("halo", req.getValue().getMessage());
        assertEquals(PHONE, req.getValue().getPhone());
        assertEquals("wa-replied", req.getValue().getReply_message_id());
        verify(persister).saveSentChat(conversationId, "m1", "TEXT", "halo", null, "agent");
        verifyNoInteractions(storageService);
        assertEquals(1, result.getSent());
    }
}
