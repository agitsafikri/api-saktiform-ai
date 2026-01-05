package com.saktiform.api.service.order;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.Order.OrderCreatedEvent;
import com.saktiform.api.model.chat.ConversationUpdatedData;
import com.saktiform.api.model.chat.GoWaSendMessageRequest;
import com.saktiform.api.repository.ChatRepository;
import com.saktiform.api.repository.ChatTemplateRepository;
import com.saktiform.api.repository.OrderRepository;
import com.saktiform.api.service.chat.ChatEventPublisher;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import com.saktiform.api.service.chat.WhatsappClientHelper;
import jakarta.transaction.Transactional;
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
    private final MessageConstructorHelper messageConstructorHelper;
    private final WhatsappClientHelper client;
    private final ChatRepository chatRepository;
    private final OrderRepository orderRepository;
    private final ChatTemplateRepository chatTemplateRepository;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public OrderEventListener(ChatEventPublisher chatPublisher,
                              MessageConstructorHelper messageConstructorHelper,
                              WhatsappClientHelper client,
                              ChatRepository chatRepository,
                              ChatTemplateRepository chatTemplateRepository,
                              OrderRepository orderRepository) {
        this.chatPublisher = chatPublisher;
        this.messageConstructorHelper = messageConstructorHelper;
        this.client = client;
        this.chatRepository = chatRepository;
        this.chatTemplateRepository = chatTemplateRepository;
        this.orderRepository = orderRepository;
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    protected void autoFollowup(OrderCreatedEvent orderCreatedEvent){
        var order = orderRepository.findById(orderCreatedEvent.getOrderId()).get();
        var template = chatTemplateRepository.getByCategoryAndIdWorkspace("FOLLOWUP", order.getProduk().getIdWorkspace());
        var templateParam = messageConstructorHelper.buildOrderParams(order.getId());
        var followUpText = messageConstructorHelper.fillTemplate(template.getContent(), templateParam);

        var sendMessageRequest = new GoWaSendMessageRequest();
        sendMessageRequest.setMessage(followUpText);
        sendMessageRequest.setPhone(order.getNomorWhatsapp());

        var produk = order.getProduk();
        var workspace = produk.getWorkspace();
        var waba = workspace.getWaba();
        var port = waba.getPort();


        var response = client.sendMessage(port, sendMessageRequest);

        var chat = new Chat();
        if (response.getCode().equals("SUCCESS")) {
            chat.setIdConversation(order.getIdConversation());
            chat.setMessageId(response.getResults().getMessage_id());
            chat.setType("TEXT");
            chat.setSentAt(Instant.now());

            chat.setPengirim("BOT");
            chat.setPesan(followUpText);

            var savedChat = chatRepository.save(chat);


            var conversationUpdatedData = new ConversationUpdatedData();
            conversationUpdatedData.setId(order.getIdConversation());
            conversationUpdatedData.setLastMessage(savedChat.getPesan());
            conversationUpdatedData.setLastMessageType(savedChat.getType());
            conversationUpdatedData.setStatus(order.getConversation().getStatus());
            conversationUpdatedData.setContactName(order.getContact().getNamaKontak());
            conversationUpdatedData.setLastMessageTime(chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta"))
                    .format(formatter));

            chatPublisher.publishConversationUpdated(order.getProduk().getIdWorkspace(), conversationUpdatedData, conversationUpdatedData.getLastMessageTime());
        }
    }
}
