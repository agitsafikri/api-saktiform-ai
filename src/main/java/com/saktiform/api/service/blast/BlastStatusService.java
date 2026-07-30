package com.saktiform.api.service.blast;

import com.saktiform.api.entity.BlastMessage;
import com.saktiform.api.entity.BlastMessageEvent;
import com.saktiform.api.entity.Chat;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.blast.ReplyPayload;
import com.saktiform.api.model.blast.enums.MessageStatus;
import com.saktiform.api.model.chat.bot.IncomingChatEvent;
import com.saktiform.api.repository.BlastCampaignRepository;
import com.saktiform.api.repository.BlastMessageEventRepository;
import com.saktiform.api.repository.BlastMessageRepository;
import com.saktiform.api.repository.ConversationRepository;
import com.saktiform.api.service.chat.ChatMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Update status delivery (webhook) & deteksi balasan (reply) + penyimpanan balasan pertama (FR-13).
 */
@Service
public class BlastStatusService {

    private static final Logger log = LoggerFactory.getLogger(BlastStatusService.class);

    /** Rank status untuk webhook out-of-order (BR-14). */
    private static final Map<String, Integer> RANK = Map.of(
            MessageStatus.WAITING.name(), 0,
            MessageStatus.SENDING.name(), 1,
            MessageStatus.SENT.name(), 2,
            MessageStatus.DELIVERED.name(), 3,
            MessageStatus.READ.name(), 4,
            MessageStatus.REPLIED.name(), 5);

    private final BlastMessageRepository messageRepository;
    private final BlastMessageEventRepository eventRepository;
    private final BlastCampaignRepository campaignRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageService chatMessageService;

    @Value("${blast.reply.window-days:7}")
    private int replyWindowDays;

    public BlastStatusService(BlastMessageRepository messageRepository,
                              BlastMessageEventRepository eventRepository,
                              BlastCampaignRepository campaignRepository,
                              ConversationRepository conversationRepository,
                              ChatMessageService chatMessageService) {
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.campaignRepository = campaignRepository;
        this.conversationRepository = conversationRepository;
        this.chatMessageService = chatMessageService;
    }

    /** Webhook delivery: terapkan DELIVERED/READ hanya bila rank naik (idempoten, BR-14). */
    @Transactional
    public void applyDeliveryStatus(String providerMessageId, String status, Instant ts) {
        if (providerMessageId == null || providerMessageId.isBlank()) return;
        MessageStatus target = mapProviderStatus(status);
        if (target == null) return;

        Optional<BlastMessage> opt = messageRepository.findFirstByProviderMessageId(providerMessageId);
        if (opt.isEmpty()) return; // message_id tidak dikenal → abaikan

        BlastMessage msg = opt.get();
        int curRank = RANK.getOrDefault(msg.getStatus(), 0);
        int toRank = RANK.getOrDefault(target.name(), 0);
        if (toRank <= curRank) return; // out-of-order / duplikat → abaikan

        Instant now = Instant.now();
        Instant effectiveTs = ts != null ? ts : now;
        String from = msg.getStatus();
        msg.setStatus(target.name());
        if (target == MessageStatus.DELIVERED) msg.setDeliveredAt(effectiveTs);
        else if (target == MessageStatus.READ) msg.setReadAt(effectiveTs);
        else if (target == MessageStatus.SENT && msg.getSentAt() == null) msg.setSentAt(effectiveTs);
        msg.setUpdatedAt(now);
        messageRepository.save(msg);
        appendEvent(msg, from, target.name(), "WEBHOOK", null);
        // DELIVERED/READ tidak mengubah counter (sudah terhitung sebagai SENT di worker).
    }

    /** Deteksi balasan + simpan balasan pertama (FR-13, BR-23). Idempotent: hanya balasan pertama. */
    @Transactional
    public void markReplied(Long idWorkspace, String phone, ReplyPayload reply) {
        if (idWorkspace == null || phone == null || phone.isBlank()) return;
        Instant replyAt = reply.createdAt() != null ? reply.createdAt() : Instant.now();
        Instant windowStart = replyAt.minus(Duration.ofDays(replyWindowDays));

        Optional<BlastMessage> opt = messageRepository.findRepliable(idWorkspace, phone, windowStart);
        if (opt.isEmpty()) return; // bukan recipient blast / di luar window / sudah terminal

        BlastMessage msg = opt.get();
        if (msg.getFirstReplyChatId() != null) return; // balasan pertama sudah tersimpan (idempotent)

        String from = msg.getStatus();
        Instant now = Instant.now();
        msg.setStatus(MessageStatus.REPLIED.name());
        msg.setRepliedAt(replyAt);
        msg.setFirstReplyChatId(reply.chatId());
        msg.setFirstReplyMessage(reply.message());
        msg.setFirstReplyMediaType(reply.mediaType());
        msg.setFirstReplyMediaLink(reply.mediaPath());
        msg.setUpdatedAt(now);
        messageRepository.save(msg);

        campaignRepository.onReplied(msg.getCampaignId());
        appendEvent(msg, from, MessageStatus.REPLIED.name(), "REPLY", null);
    }

    /**
     * Hook pesan masuk: setiap Chat customer masuk dicek apakah membalas pesan blast (non-invasif,
     * tanpa mengubah WhatsappMessageHandler). Berjalan async setelah pesan masuk commit.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncomingChat(IncomingChatEvent event) {
        try {
            Chat chat = chatMessageService.findById(event.getChatId());
            if (chat == null || chat.getIdConversation() == null) return;
            Conversation conv = conversationRepository.findById(chat.getIdConversation()).orElse(null);
            if (conv == null || conv.getContact() == null) return;

            ReplyPayload reply = new ReplyPayload(
                    chat.getId(), chat.getPesan(), chat.getType(), chat.getMedia(),
                    chat.getSentAt() != null ? chat.getSentAt() : Instant.now());
            markReplied(conv.getContact().getIdWorkspace(), conv.getContact().getPhoneNumber(), reply);
        } catch (Exception e) {
            log.error("Reply detection gagal untuk chatId={}: {}", event.getChatId(), e.getMessage());
        }
    }

    private MessageStatus mapProviderStatus(String status) {
        if (status == null) return null;
        return switch (status.trim().toLowerCase()) {
            case "sent" -> MessageStatus.SENT;
            case "delivered", "deliver" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            default -> null;
        };
    }

    private void appendEvent(BlastMessage msg, String from, String to, String source, String detail) {
        BlastMessageEvent e = new BlastMessageEvent();
        e.setMessageId(msg.getId());
        e.setIdWorkspace(msg.getIdWorkspace());
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setSource(source);
        e.setDetail(detail);
        e.setCreatedAt(Instant.now());
        eventRepository.save(e);
    }
}
