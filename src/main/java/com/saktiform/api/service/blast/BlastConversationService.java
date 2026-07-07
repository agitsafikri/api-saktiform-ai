package com.saktiform.api.service.blast;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.entity.Contact;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.chat.ChatListDto;
import com.saktiform.api.model.chat.ConversationUpdatedData;
import com.saktiform.api.model.chat.RemovedConversation;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.service.AppConfigService;
import com.saktiform.api.service.StorageService;
import com.saktiform.api.service.chat.ChatMessageService;
import com.saktiform.api.service.chat.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Menempelkan pesan blast yang berhasil terkirim ke Chat Room (FR-12): find-or-create Contact +
 * Conversation, simpan Chat keluar, auto-assign (FR-12.9/BR-20), dan pancarkan event WebSocket
 * yang sama seperti alur chat live (NEW_MESSAGE + assigned/unassigned events).
 *
 * <p>Berjalan dalam transaksi {@code REQUIRES_NEW} sehingga kegagalan penempelan TIDAK me-rollback
 * status SENT pesan blast (mencegah re-send duplikat). Semantik mengikuti
 * {@code ChatService.messageHandler}/{@code takeoverConversation}; ekstraksi method bersama dari
 * ChatService = follow-up (sengaja tidak mengubah ChatService demi menjaga fitur chat live).
 */
@Service
public class BlastConversationService {

    private static final Logger log = LoggerFactory.getLogger(BlastConversationService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final String UNASSIGNED = "UNASSIGNED";
    private static final String ASSIGNED = "ASSIGNED";

    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final AccountRepository accountRepository;
    private final AppConfigService appConfigService;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    public BlastConversationService(ConversationService conversationService,
                                    ChatMessageService chatMessageService,
                                    AccountRepository accountRepository,
                                    AppConfigService appConfigService,
                                    StorageService storageService,
                                    ApplicationEventPublisher eventPublisher) {
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
        this.accountRepository = accountRepository;
        this.appConfigService = appConfigService;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
    }

    public record OutboundResult(UUID conversationId, UUID chatId) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboundResult recordOutboundChat(Long idWorkspace, String phone, String name,
                                             String providerMessageId, String type, String message,
                                             String mediaPath, Long assigneeAccountId) {
        Instant now = Instant.now();

        // 1) find-or-create Contact (scoped workspace, idempotent)
        Contact contact = conversationService.findContactByPhoneNumberAndIdWorkspace(phone, idWorkspace);
        if (contact == null) {
            contact = new Contact();
            contact.setPhoneNumber(phone);
            contact.setNamaKontak(name);
            contact.setIdWorkspace(idWorkspace);
            contact.setCreatedAt(now);
            try {
                contact = conversationService.saveContact(contact);
            } catch (DataIntegrityViolationException race) {
                contact = conversationService.findContactByPhoneNumberAndIdWorkspace(phone, idWorkspace);
                if (contact == null) throw race;
            }
        }

        // 2) find-or-create Conversation (1:1 dengan Contact)
        Conversation conversation = conversationService.findByIdContact(contact.getId());
        boolean created = false;
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setIdContact(contact.getId());
            conversation.setStatus(UNASSIGNED);
            conversation.setChatStatus("OPEN");
            conversation.setSource("BLAST");
            conversation.setHandleByBot(true);
            conversation.setUnreadMessageCount(0);
            conversation.setBotQuota(defaultBotQuota());
            conversation.setCreatedAt(now);
            conversation = conversationService.saveConversation(conversation);
            created = true;
        }

        // 3) Auto-assign jika baru / UNASSIGNED (FR-12.9/BR-20); jangan ganti jika sudah ASSIGNED (BR-21)
        boolean autoAssigned = false;
        if ((created || UNASSIGNED.equalsIgnoreCase(conversation.getStatus())) && assigneeAccountId != null) {
            conversation.setStatus(ASSIGNED);
            conversation.setHandledBy(assigneeAccountId);
            conversation.setHandleByBot(false);
            conversation.setChatStatus("");
            autoAssigned = true;
        }

        // 4) Simpan Chat keluar (pengirim = BLAST-<kode pembuat>, OQ-19). Tidak menaikkan unread (FR-12.6)
        String pengirim = blastSenderLabel(assigneeAccountId);
        Chat chat = new Chat();
        chat.setIdConversation(conversation.getId());
        chat.setMessageId(providerMessageId);
        chat.setType(type);
        chat.setPengirim(pengirim);
        chat.setPesan(message);
        chat.setMedia(mediaPath);
        chat.setStatus("SENT");
        chat.setSentAt(now);
        chat.setCreatedAt(now);
        Chat savedChat = chatMessageService.saveChat(chat);

        // 5) Update conversation last message
        conversation.setLastMessage(message);
        conversation.setLastMessageType(type);
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversation = conversationService.saveConversation(conversation);

        // 6) Publish event WebSocket (emit penuh — OQ-18)
        publishEvents(conversation, contact.getNamaKontak(), savedChat, idWorkspace, autoAssigned);

        return new OutboundResult(conversation.getId(), savedChat.getId());
    }

    private void publishEvents(Conversation conversation, String contactName, Chat chat,
                               Long idWorkspace, boolean autoAssigned) {
        String ts = chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta")).format(FORMATTER);

        // NEW_MESSAGE ke chatroom
        ChatListDto chatDto = new ChatListDto(
                chat.getId(), chat.getType(), chat.getPengirim(), chat.getPesan(),
                chat.getMedia() != null ? storageService.getPublicUrl(chat.getMedia()) : null,
                chat.getSentAt());
        eventPublisher.publishEvent(ChatAsyncEvent.builder()
                .eventType(ChatAsyncEvent.EventType.NEW_MESSAGE)
                .conversationId(conversation.getId())
                .data(chatDto)
                .timestamp(ts)
                .build());

        ConversationUpdatedData data = new ConversationUpdatedData();
        data.setId(conversation.getId());
        data.setLastMessage(chat.getPesan());
        data.setLastMessageType(chat.getType());
        data.setStatus(conversation.getStatus());
        data.setChatStatus(conversation.getChatStatus());
        data.setContactName(contactName);
        data.setUnreadMessageCount(conversation.getUnreadMessageCount());
        data.setLastMessageTime(ts);

        if (autoAssigned) {
            // Pindah dari daftar unassigned → assigned milik assignee (setara takeoverConversation)
            RemovedConversation removed = new RemovedConversation();
            removed.setConversationId(conversation.getId());
            eventPublisher.publishEvent(ChatAsyncEvent.builder()
                    .eventType(ChatAsyncEvent.EventType.UNASSIGNED_CONVERSATION_REMOVED)
                    .workspaceId(idWorkspace)
                    .data(removed)
                    .timestamp(ts)
                    .build());
            eventPublisher.publishEvent(ChatAsyncEvent.builder()
                    .eventType(ChatAsyncEvent.EventType.ASSIGNED_CONVERSATION_CREATED)
                    .workspaceId(idWorkspace)
                    .data(data)
                    .timestamp(ts)
                    .build());
        } else {
            eventPublisher.publishEvent(ChatAsyncEvent.builder()
                    .eventType(ChatAsyncEvent.EventType.ASSIGNED_CONVERSATION_UPDATED)
                    .workspaceId(idWorkspace)
                    .data(data)
                    .timestamp(ts)
                    .build());
        }
    }

    private String blastSenderLabel(Long assigneeAccountId) {
        String code = "SYSTEM";
        if (assigneeAccountId != null) {
            var acc = accountRepository.findById(assigneeAccountId);
            if (acc.isPresent() && acc.get().getUsername() != null) {
                code = acc.get().getUsername();
            } else {
                code = String.valueOf(assigneeAccountId);
            }
        }
        return "BLAST-" + code;
    }

    private Integer defaultBotQuota() {
        try {
            return Integer.valueOf(appConfigService.getConfig("bot.default.quota"));
        } catch (Exception e) {
            log.warn("Tidak bisa membaca config bot.default.quota, default 0");
            return 0;
        }
    }
}
