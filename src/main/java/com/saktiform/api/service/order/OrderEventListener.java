package com.saktiform.api.service.order;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.Order.OrderCreatedEvent;
import com.saktiform.api.model.chat.ChatListDto;
import com.saktiform.api.model.chat.ChatStatus;
import com.saktiform.api.model.chat.ConversationUpdatedData;
import com.saktiform.api.model.chat.GoWaSendMessageRequest;
import com.saktiform.api.repository.OrderRepository;
import com.saktiform.api.service.AppConfigService;
import com.saktiform.api.service.MessageTemplateService;
import com.saktiform.api.service.StorageService;
import com.saktiform.api.service.chat.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class OrderEventListener {
    private final ChatEventPublisher chatPublisher;
    private final WhatsappClientHelper client;
    private final OrderRepository orderRepository;
    private final MessageTemplateService messageTemplateService;
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final AppConfigService appConfigService;
    private final StorageService storageService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public OrderEventListener(ChatEventPublisher chatPublisher,
                              WhatsappClientHelper client,
                              OrderRepository orderRepository,
                              MessageTemplateService messageTemplateService,
                              ConversationService conversationService,
                              ChatMessageService chatMessageService, AppConfigService appConfigService, StorageService storageService) {
        this.chatPublisher = chatPublisher;
        this.messageTemplateService = messageTemplateService;
        this.client = client;
        this.orderRepository = orderRepository;
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
        this.appConfigService = appConfigService;
        this.storageService = storageService;
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    protected void autoFollowup(OrderCreatedEvent orderCreatedEvent){
        var order = orderRepository.findById(orderCreatedEvent.getOrderId()).get();
        String textMessage;

        textMessage = messageTemplateService.getFollowUpText(
            order.getProduk().getIdWorkspace(),
            order.getId(),
            orderCreatedEvent.getType()
        );

        var sendMessageRequest = new GoWaSendMessageRequest();
        sendMessageRequest.setMessage(textMessage);
        sendMessageRequest.setPhone(order.getNomorWhatsapp());

        var produk = order.getProduk();
        var workspace = produk.getWorkspace();
        var waba = workspace.getWaba();
        var deviceId = waba.getId().toString();
        Boolean newConvo;

        var response = client.sendMessage(deviceId, sendMessageRequest);
        if(response.getCode().equals("SUCCESS")) {
            Conversation conversation = conversationService.findByIdContact(order.getContact().getId());
            if (conversation == null) {
                Conversation newConversation = new Conversation();
                newConversation.setStatus("UNASSIGNED");
                newConversation.setChatStatus(ChatStatus.OPEN.name());
                newConversation.setIdContact(order.getContact().getId());
                newConversation.setCreatedAt(Instant.now());
                newConversation.setLastMessageAt(Instant.now());
                newConversation.setHandleByBot(true);
                newConversation.setUnreadMessageCount(0);
                newConversation.setBotQuota(Integer.valueOf(appConfigService.getConfig("bot.default.quota")));
                conversation = conversationService.save(newConversation);

                newConvo = true;
            }else {
                newConvo = false;
            }

            order.setIdConversation(conversation.getId());
            orderRepository.save(order);

            var chat = new Chat();
            if (response.getCode().equals("SUCCESS")) {
                chat.setStatus("SENT");
            }else {
                chat.setStatus("FAILED");
            }
            chat.setIdConversation(conversation.getId());
            chat.setMessageId(response.getResults().getMessage_id());
            chat.setType("TEXT");
            chat.setSentAt(Instant.now());
            chat.setPengirim("BOT");
            chat.setPesan(textMessage);

            var savedChat = chatMessageService.saveChat(chat);

            conversation.setActiveOrderId(order.getId());
            conversation.setLastMessage(chat.getPesan());
            conversation.setLastMessageAt(chat.getSentAt());
            conversation.setLastMessageType(chat.getType());

            conversationService.save(conversation);

            var conversationUpdatedData = new ConversationUpdatedData();
            conversationUpdatedData.setUnreadMessageCount(conversation.getUnreadMessageCount());
            conversationUpdatedData.setId(order.getIdConversation());
            conversationUpdatedData.setLastMessage(savedChat.getPesan());
            conversationUpdatedData.setLastMessageType(savedChat.getType());
            conversationUpdatedData.setStatus(conversation.getStatus());
            conversationUpdatedData.setChatStatus(conversation.getChatStatus());
            conversationUpdatedData.setContactName(order.getContact().getNamaKontak());
            conversationUpdatedData.setLastMessageTime(chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta"))
                    .format(formatter));

            var newChatUpdate = new ChatListDto(chat.getId()
                    , chat.getType()
                    , chat.getPengirim()
                    , chat.getPesan()
                    , chat.getMedia() != null ? storageService.getPublicUrl(chat.getMedia()) : null
                    , chat.getSentAt());

            if (newConvo) {
                chatPublisher.publishUnassignedConversationCreated(order.getProduk().getIdWorkspace(), conversationUpdatedData, conversationUpdatedData.getLastMessageTime());
            }else {
                if (conversation.getStatus().equals("ASSIGNED")) {
                    chatPublisher.publishAssignedConversationUpdated(order.getProduk().getIdWorkspace(), conversationUpdatedData, conversationUpdatedData.getLastMessageTime());
                }else {
                    chatPublisher.publishUnassignedConversationUpdated(order.getProduk().getIdWorkspace(), conversationUpdatedData, conversationUpdatedData.getLastMessageTime());
                }

                chatPublisher.publishNewMessage(conversation.getId(), newChatUpdate, conversationUpdatedData.getLastMessageTime());
            }

        }
    }
}
