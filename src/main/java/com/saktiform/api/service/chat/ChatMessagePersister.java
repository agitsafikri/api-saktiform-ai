package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.chat.ChatListDto;
import com.saktiform.api.model.chat.ChatSendContext;
import com.saktiform.api.model.chat.ConversationUpdatedData;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.repository.ConversationRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Bagian persistensi dari pengiriman pesan keluar. Dipisah dari {@link ChatService} agar
 * {@code @Transactional} lewat proxy Spring: tanpa transaksi luar setiap call commit sendiri
 * (commit per pesan), dengan transaksi luar (bot) ikut join transaksi tersebut.
 */
@Service
public class ChatMessagePersister {
        private final ConversationRepository conversationRepository;
        private final WorkspaceRepository workspaceRepository;
        private final ChatMessageService chatMessageService;
        private final StorageService storageService;
        private final ApplicationEventPublisher eventPublisher;

        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

        public ChatMessagePersister(ConversationRepository conversationRepository,
                        WorkspaceRepository workspaceRepository,
                        ChatMessageService chatMessageService,
                        StorageService storageService,
                        ApplicationEventPublisher eventPublisher) {
                this.conversationRepository = conversationRepository;
                this.workspaceRepository = workspaceRepository;
                this.chatMessageService = chatMessageService;
                this.storageService = storageService;
                this.eventPublisher = eventPublisher;
        }

        @Transactional(readOnly = true)
        public ChatSendContext loadContext(UUID conversationId) {
                var conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
                var contact = conversation.getContact();
                var workspace = workspaceRepository.findById(contact.getIdWorkspace())
                                .orElseThrow(() -> new RuntimeException("Workspace tidak ditemukan"));

                return new ChatSendContext(
                                conversation.getId(),
                                workspace.getId(),
                                workspace.getWaba().getId().toString(),
                                contact.getPhoneNumber(),
                                contact.getNamaKontak());
        }

        @Transactional
        public Chat saveSentChat(UUID conversationId, String messageId, String type,
                        String pesan, String mediaPath, String username) {
                var chat = new Chat();
                chat.setIdConversation(conversationId);
                chat.setMessageId(messageId);
                chat.setType(type);
                chat.setSentAt(Instant.now());
                chat.setStatus("SENT");
                chat.setPengirim(username);
                chat.setPesan(pesan);
                chat.setMedia(mediaPath);
                var savedChat = chatMessageService.saveChat(chat);

                // Publish to chatroom - so agent sees their own message
                var newChatUpdate = new ChatListDto(
                                savedChat.getId(),
                                savedChat.getType(),
                                savedChat.getPengirim(),
                                savedChat.getPesan(),
                                savedChat.getMedia() != null ? storageService.getPublicUrl(savedChat.getMedia()) : null,
                                savedChat.getSentAt());

                eventPublisher.publishEvent(ChatAsyncEvent.builder()
                                .eventType(ChatAsyncEvent.EventType.NEW_MESSAGE)
                                .conversationId(conversationId)
                                .data(newChatUpdate)
                                .timestamp(savedChat.getSentAt().atZone(ZoneId.of("Asia/Jakarta"))
                                                .format(formatter))
                                .build());

                return savedChat;
        }

        @Transactional
        public void updateConversationAfterSend(ChatSendContext ctx, Chat lastChat, String lastMessage) {
                var conversation = conversationRepository.findById(ctx.conversationId())
                                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));

                conversation.setLastMessageAt(lastChat.getSentAt());
                conversation.setLastMessage(lastMessage);
                conversation.setLastMessageType(lastChat.getType());
                conversationRepository.save(conversation);

                // Publish to conversation list
                var conversationUpdatedData = new ConversationUpdatedData();
                conversationUpdatedData.setId(conversation.getId());
                conversationUpdatedData.setLastMessage(lastMessage);
                conversationUpdatedData.setLastMessageType(lastChat.getType());
                conversationUpdatedData.setStatus(conversation.getStatus());
                conversationUpdatedData.setChatStatus(conversation.getChatStatus());
                conversationUpdatedData.setContactName(ctx.contactName());
                conversationUpdatedData.setLastMessageTime(lastChat.getSentAt().atZone(ZoneId.of("Asia/Jakarta"))
                                .format(formatter));

                eventPublisher.publishEvent(ChatAsyncEvent.builder()
                                .eventType(ChatAsyncEvent.EventType.ASSIGNED_CONVERSATION_UPDATED)
                                .workspaceId(ctx.workspaceId())
                                .data(conversationUpdatedData)
                                .timestamp(conversationUpdatedData.getLastMessageTime())
                                .build());
        }
}
