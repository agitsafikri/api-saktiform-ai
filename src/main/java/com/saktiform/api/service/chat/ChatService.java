package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.account.ConversationDetail;
import com.saktiform.api.model.chat.*;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.model.whatsapp.WhatsappResponse;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ChatService {
        @Autowired
        private WhatsappClientHelper client;

        private final WorkspaceRepository workspaceRepository;
        private final ConversationRepository conversationRepository;
        private final ApplicationEventPublisher eventPublisher;
        private final AccountRepository accountRepository;
        private final ChatMessageService chatMessageService;
        private final StorageService storageService;
        private final OrderRepository orderRepository;

//        @Value("${saktiform.api.url}")
//        private String urlApi;

        private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

        public ChatService(WorkspaceRepository workspaceRepository,
                        ApplicationEventPublisher eventPublisher,
                        AccountRepository accountRepository,
                        ConversationRepository conversationRepository,
                        OrderRepository orderRepository,
                        StorageService storageService,
                        ChatMessageService chatMessageService) {
                this.workspaceRepository = workspaceRepository;
                this.accountRepository = accountRepository;
                this.orderRepository = orderRepository;
                this.conversationRepository = conversationRepository;
                this.eventPublisher = eventPublisher;
                this.chatMessageService = chatMessageService;
                this.storageService = storageService;
        }

        @Transactional
        public void messageHandler(SendMessageDto data, String username) {
                var conversation = conversationRepository.findById(data.getConversationId())
                                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
                var contact = conversation.getContact();
                var messageType = data.getMessageType().name();
                var workspace = workspaceRepository.findById(contact.getIdWorkspace())
                                .orElseThrow(() -> new RuntimeException("Workspace tidak ditemukan"));
                var waba = workspace.getWaba();

                var chat = new Chat();
                try {
                        WhatsappResponse<SendResults> response = null;
                        if (messageType.equals("TEXT")) {
                                var sendMessageRequest = new GoWaSendMessageRequest();
                                sendMessageRequest.setMessage(data.getMessage());
                                sendMessageRequest.setPhone(contact.getPhoneNumber());
                                if (data.getRepliedMessageId() != null) {
                                        var repliedMessage = chatMessageService.findById(data.getRepliedMessageId());
                                        sendMessageRequest.setReply_message_id(repliedMessage.getMessageId());
                                }

                                response = client.sendMessage(waba.getId().toString(), sendMessageRequest);

                        } else if (messageType.equals("IMAGE")) {
                                response = client.sendImage(waba.getId().toString(), contact.getPhoneNumber(), data.getMessage(),
                                                data.getMediaLink());
                        } else if (messageType.equals("VIDEO")) {
                                response = client.sendVideo(waba.getId().toString(), contact.getPhoneNumber(), data.getMessage(),
                                                data.getMediaLink());
                        } else if (messageType.equals("DOCUMENT")) {
                                response = client.sendFile(waba.getId().toString(), contact.getPhoneNumber(), data.getMessage(),
                                                data.getMediaLink());
                        } else if (messageType.equals("AUDIO")) {
                                response = client.sendAudio(waba.getId().toString(), contact.getPhoneNumber(),
                                                data.getMediaLink());
                        }

                        assert response != null;
                        if (response.getCode().equals("SUCCESS")) {
                                chat.setIdConversation(data.getConversationId());
                                chat.setMessageId(response.getResults().getMessage_id());
                                chat.setType(messageType);
                                chat.setSentAt(Instant.now());
                                chat.setStatus("SENT");
                                chat.setPengirim(username);
                                chat.setPesan(data.getMessage());
                                chat.setMedia(data.getMediaLink()!=null?storageService.extractPathFromPublicUrl(data.getMediaLink()):null);
                                var savedChat = chatMessageService.saveChat(chat);

                                conversation.setLastMessageAt(savedChat.getSentAt());
                                conversation.setLastMessage(savedChat.getPesan());
                                conversation.setLastMessageType(savedChat.getType());

                                conversationRepository.save(conversation);

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
                                                .conversationId(conversation.getId())
                                                .data(newChatUpdate)
                                                .timestamp(chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta"))
                                                                .format(formatter))
                                                .build());

                                // Publish to conversation list
                                var conversationUpdatedData = new ConversationUpdatedData();
                                conversationUpdatedData.setId(conversation.getId());
                                conversationUpdatedData.setLastMessage(savedChat.getPesan());
                                conversationUpdatedData.setLastMessageType(savedChat.getType());
                                conversationUpdatedData.setStatus(conversation.getStatus());
                                conversationUpdatedData.setChatStatus(conversation.getChatStatus());
                                conversationUpdatedData.setContactName(contact.getNamaKontak());
                                conversationUpdatedData.setLastMessageTime(chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta"))
                                                                .format(formatter));

                                eventPublisher.publishEvent(ChatAsyncEvent.builder()
                                                .eventType(ChatAsyncEvent.EventType.ASSIGNED_CONVERSATION_UPDATED)
                                                .workspaceId(workspace.getId())
                                                .data(conversationUpdatedData)
                                                .timestamp(conversationUpdatedData.getLastMessageTime())
                                                .build());

                        } else {

                                throw new RuntimeException("Gagal mengirim pesan ke whatsapp");
                        }
                } catch (Exception e) {
                        throw new RuntimeException(
                                        "Terjadi kesalahan saat mengirim pesan ke whatsapp, silahkan coba lagi.");
                }

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

                String selectedOrder="";
                if (conversation.getActiveOrderId() != null) {
                        selectedOrder = orderRepository.findById(conversation.getActiveOrderId()).get().getOrderCode();
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
