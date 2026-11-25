package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.account.ConversationDetail;
import com.saktiform.api.model.chat.*;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.model.whatsapp.WhatsappResponse;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatService {
        private static final Logger log = LoggerFactory.getLogger(ChatService.class);

        @Autowired
        private WhatsappClientHelper client;

        private final WorkspaceRepository workspaceRepository;
        private final ConversationRepository conversationRepository;
        private final ApplicationEventPublisher eventPublisher;
        private final AccountRepository accountRepository;
        private final ChatMessageService chatMessageService;
        private final StorageService storageService;
        private final OrderRepository orderRepository;
        private final ChatMessagePersister chatMessagePersister;

        @Value("${chat.multi-image.max:10}")
        private int maxImages;

        @Value("${chat.multi-image.delay-ms:0}")
        private long delayMs;

//        @Value("${saktiform.api.url}")
//        private String urlApi;

        private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        public ChatService(WorkspaceRepository workspaceRepository,
                        ApplicationEventPublisher eventPublisher,
                        AccountRepository accountRepository,
                        ConversationRepository conversationRepository,
                        OrderRepository orderRepository,
                        StorageService storageService,
                        ChatMessageService chatMessageService,
                        ChatMessagePersister chatMessagePersister) {
                this.workspaceRepository = workspaceRepository;
                this.accountRepository = accountRepository;
                this.orderRepository = orderRepository;
                this.conversationRepository = conversationRepository;
                this.eventPublisher = eventPublisher;
                this.chatMessageService = chatMessageService;
                this.storageService = storageService;
                this.chatMessagePersister = chatMessagePersister;
        }

        /**
         * Kirim pesan keluar. Untuk IMAGE dapat berisi beberapa gambar: dikirim berurutan, satu pesan
         * WhatsApp dan satu baris Chat per gambar, caption hanya di gambar pertama. Berhenti pada
         * gambar pertama yang gagal; gambar yang sudah terkirim tetap tersimpan.
         *
         * Sengaja tidak @Transactional: panggilan HTTP ke provider berada di luar transaksi dan
         * persistensi dilakukan per pesan oleh {@link ChatMessagePersister}.
         */
        public SendMessageResult messageHandler(SendMessageDto data, String username) {
                var ctx = chatMessagePersister.loadContext(data.getConversationId());
                var type = data.getMessageType();
                var mediaUrls = validateMediaLinks(type, data.getMediaLink());
                // Gambar bucket produk (mis. dari template) disalin ke bucket chat agar tampil benar di riwayat chat.
                var mediaPaths = mediaUrls.stream().map(storageService::toChatBucketPath).toList();

                int total = type == ChatType.TEXT ? 1 : mediaUrls.size();
                List<Chat> sentChats = new ArrayList<>();

                for (int i = 0; i < total; i++) {
                        if (i > 0 && delayMs > 0) {
                                try {
                                        Thread.sleep(delayMs);
                                } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                }
                        }

                        boolean isFirst = i == 0;
                        String mediaUrl = type == ChatType.TEXT ? null : mediaUrls.get(i);

                        WhatsappResponse<SendResults> response;
                        try {
                                response = sendToProvider(ctx, type, isFirst ? data.getMessage() : "", mediaUrl,
                                                isFirst ? data.getRepliedMessageId() : null);
                        } catch (Exception e) {
                                log.error("Gagal mengirim pesan ke whatsapp, conversation {} index {}",
                                                ctx.conversationId(), i, e);
                                response = null;
                        }

                        if (response == null || !"SUCCESS".equals(response.getCode()) || response.getResults() == null) {
                                break;
                        }

                        sentChats.add(chatMessagePersister.saveSentChat(
                                        ctx.conversationId(),
                                        response.getResults().getMessage_id(),
                                        type.name(),
                                        isFirst ? data.getMessage() : null,
                                        type == ChatType.TEXT ? null : mediaPaths.get(i),
                                        username));
                }

                if (sentChats.isEmpty()) {
                        throw new RuntimeException(
                                        "Terjadi kesalahan saat mengirim pesan ke whatsapp, silahkan coba lagi.");
                }

                var lastChat = sentChats.getLast();
                var lastMessage = lastChat.getPesan() != null ? lastChat.getPesan() : sentChats.getFirst().getPesan();
                chatMessagePersister.updateConversationAfterSend(ctx, lastChat, lastMessage);

                return new SendMessageResult(total, sentChats.size(), total - sentChats.size(),
                                sentChats.stream().map(Chat::getId).toList());
        }

        private List<String> validateMediaLinks(ChatType type, List<String> rawLinks) {
                if (type == null) {
                        throw new IllegalArgumentException("Tipe pesan wajib diisi");
                }
                if (type == ChatType.TEXT) {
                        return List.of();
                }

                List<String> links = rawLinks == null ? List.of()
                                : rawLinks.stream().filter(Objects::nonNull).filter(l -> !l.isBlank()).toList();

                if (type == ChatType.IMAGE) {
                        if (links.isEmpty()) {
                                throw new IllegalArgumentException("Gambar wajib diisi");
                        }
                        if (links.size() > maxImages) {
                                throw new IllegalArgumentException("Maksimal " + maxImages + " gambar per pengiriman");
                        }
                } else {
                        if (links.isEmpty()) {
                                throw new IllegalArgumentException("File wajib diisi");
                        }
                        if (links.size() > 1) {
                                throw new IllegalArgumentException("Tipe " + type.name() + " hanya mendukung 1 file");
                        }
                }
                return links;
        }

        private WhatsappResponse<SendResults> sendToProvider(ChatSendContext ctx, ChatType type, String caption,
                        String mediaUrl, UUID repliedMessageId) throws Exception {
                return switch (type) {
                        case TEXT -> {
                                var sendMessageRequest = new GoWaSendMessageRequest();
                                sendMessageRequest.setMessage(caption);
                                sendMessageRequest.setPhone(ctx.phoneNumber());
                                if (repliedMessageId != null) {
                                        var repliedMessage = chatMessageService.findById(repliedMessageId);
                                        sendMessageRequest.setReply_message_id(repliedMessage.getMessageId());
                                }
                                yield client.sendMessage(ctx.deviceId(), sendMessageRequest);
                        }
                        case IMAGE -> client.sendImage(ctx.deviceId(), ctx.phoneNumber(), caption, mediaUrl);
                        case VIDEO -> client.sendVideo(ctx.deviceId(), ctx.phoneNumber(), caption, mediaUrl);
                        case DOCUMENT -> client.sendFile(ctx.deviceId(), ctx.phoneNumber(), caption, mediaUrl);
                        case AUDIO -> client.sendAudio(ctx.deviceId(), ctx.phoneNumber(), mediaUrl);
                };
        }

        @Transactional
        public void takeoverConversation(UUID idConversation, String username) {
                var conversation = conversationRepository.findById(idConversation)
                                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
                var account = accountRepository.findByUsername(username)
                                .orElseThrow(() -> new RuntimeException("Account tidak ditemukan"));

                var recentStatus = conversation.getStatus();

                conversation.setStatus("ASSIGNED");
                conversation.setChatStatus("");
                conversation.setHandledBy(account.getId());
                // Conversation yang sudah dipegang agent tidak boleh lagi dibalas bot.
                conversation.setHandleByBot(false);


                var now = Instant.now().atZone(ZoneId.of("Asia/Jakarta"))
                        .format(formatter);
                var savedConversation = conversationRepository.save(conversation);
                var removedConv = new RemovedConversation();
                removedConv.setConversationId(savedConversation.getId());

                var workspace = workspaceRepository.findById(conversation.getContact().getIdWorkspace()).get();
                if(recentStatus.equalsIgnoreCase("UNASSIGNED")){

                        eventPublisher.publishEvent(ChatAsyncEvent.builder()
                                .eventType(ChatAsyncEvent.EventType.UNASSIGNED_CONVERSATION_REMOVED)
                                .workspaceId(workspace.getId())
                                .data(removedConv)
                                .timestamp(now)
                                .build());
                        var chat = chatMessageService.findByIdConversationOrderBySentAtDesc(conversation.getId());

                        var conversationUpdatedData = new ConversationUpdatedData();
                        conversationUpdatedData.setUnreadMessageCount(conversation.getUnreadMessageCount());
                        conversationUpdatedData.setId(conversation.getId());
                        conversationUpdatedData.setLastMessage(chat.getPesan());
                        conversationUpdatedData.setLastMessageType(chat.getType());
                        conversationUpdatedData.setStatus(conversation.getStatus());
                        conversationUpdatedData.setChatStatus(conversation.getChatStatus());
                        conversationUpdatedData.setContactName(conversation.getContact().getNamaKontak());
                        conversationUpdatedData.setLastMessageTime(chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta"))
                                .format(formatter));

                        eventPublisher.publishEvent(ChatAsyncEvent.builder()
                                .eventType(ChatAsyncEvent.EventType.ASSIGNED_CONVERSATION_CREATED)
                                .workspaceId(workspace.getId())
                                .data(conversationUpdatedData)
                                .timestamp(now)
                                .build());
                }


                var conversationDetail = new ConversationDetail();
                conversationDetail.setId(conversation.getId());
                conversationDetail.setPhoneNumber(conversation.getContact().getPhoneNumber());
                conversationDetail.setNamaKontak(conversation.getContact().getNamaKontak());
                conversationDetail.setStatus(conversation.getChatStatus());
                conversationDetail.setHandledBy(account.getUsername());

                // Order aktif yang sudah DELETE tidak ditampilkan sebagai pesanan terpilih.
                String selectedOrder="";
                if (conversation.getActiveOrderId() != null) {
                        selectedOrder = orderRepository.findActiveOrderCode(conversation.getActiveOrderId()).orElse("");
                }

                conversationDetail.setSelectedOrder(selectedOrder);

                eventPublisher.publishEvent(ChatAsyncEvent.builder().eventType(ChatAsyncEvent.EventType.CONVERSATION_DETAIL_UPDATED)
                        .workspaceId(workspace.getId())
                                .conversationId(conversation.getId())
                        .data(conversationDetail)
                        .timestamp(now)
                        .build());
        }

        public void decreaseBotQuota(UUID idConversation){
                var conversation = conversationRepository.findById(idConversation)
                                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
                var reaminingQuota = conversation.getBotQuota() - 1;
                conversation.setBotQuota(reaminingQuota);

                if(reaminingQuota == 0){
                        conversation.setChatStatus("PENDING");
                        conversation.setHandleByBot(false);
                }

                conversationRepository.save(conversation);
        }

        public void escelateToAdmin(UUID idConversation){
                var conversation = conversationRepository.findById(idConversation)
                        .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
                conversation.setChatStatus("PENDING");
                conversation.setHandleByBot(false);
                conversationRepository.save(conversation);
        }

}
