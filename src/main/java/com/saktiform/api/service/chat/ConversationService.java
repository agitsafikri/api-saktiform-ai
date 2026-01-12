package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Contact;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.Order.ConversationOrderList;
import com.saktiform.api.model.account.ConversationDetail;
import com.saktiform.api.model.chat.ConversationSelectOrder;
import com.saktiform.api.model.chat.ChatListDto;
import com.saktiform.api.model.chat.ConversationDto;
import com.saktiform.api.model.chat.QuickChatRequest;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.repository.*;
import com.saktiform.api.model.chat.QuickChatResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final ChatRepository chatRepository;
    private final OrderRepository orderRepository;
    private final ChatTemplateRepository chatTemplateRepository;
    private final MessageConstructorHelper messageConstructorHelper;
    private final ApplicationEventPublisher eventPublisher;

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ConversationService(ConversationRepository conversationRepository,
            ChatRepository chatRepository,
            OrderRepository orderRepository,
            ChatTemplateRepository chatTemplateRepository,
            MessageConstructorHelper messageConstructorHelper,
            ContactRepository contactRepository,
            ApplicationEventPublisher eventPublisher) {
        this.chatRepository = chatRepository;
        this.conversationRepository = conversationRepository;
        this.orderRepository = orderRepository;
        this.chatTemplateRepository = chatTemplateRepository;
        this.messageConstructorHelper = messageConstructorHelper;
        this.eventPublisher = eventPublisher;
        this.contactRepository = contactRepository;
    }

    public Page<ConversationDto> getUnassignedChat(Long idWorkspace, Integer page, Integer limit) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "updated_at"));

        return conversationRepository.getConversation(idWorkspace, "UNASSIGNED", pageable);
    }

    public Page<ConversationDto> getAssignedChat(Long idWorkspace, Integer page, Integer limit) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "updated_at"));
        return conversationRepository.getConversation(idWorkspace, "ASSIGNED", pageable);
    }

    public Page<ChatListDto> getListMessage(UUID idConversation, Integer page, Integer limit) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return chatRepository.getMessageList(idConversation, pageable);
    }

    public List<ConversationOrderList> getConversationOrder(UUID idConversation) {
        return orderRepository.getConversationOrderList(idConversation);
    }

    public ConversationDetail getConversationDetail(UUID idConversation) {
        var conversation = conversationRepository.findById(idConversation)
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
        var conversationDetail = new ConversationDetail();
        conversationDetail.setId(conversation.getId());

        if (conversation.getHandledBy() != null) {
            conversationDetail.setHandledBy(conversation.getHandledByAccount().getUsername());
        } else {
            conversationDetail.setHandledBy("BOT SAKTIFORM");
        }
        conversationDetail.setStatus(conversation.getStatus());

        return conversationDetail;
    }

    public void selectConversationOrder(ConversationSelectOrder order) {
        var conversation = conversationRepository.findById(order.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
        conversation.setActiveOrderId(order.getOrderId());

        var savedConversation = conversationRepository.save(conversation);

        var conversationDetail = getConversationDetail(savedConversation.getId());
        var now = Instant.now().atZone(ZoneId.of("Asia/Jakarta"))
                .format(formatter);
        eventPublisher.publishEvent(ChatAsyncEvent.builder()
                .eventType(ChatAsyncEvent.EventType.CONVERSATION_DETAIL_UPDATED)
                .conversationId(savedConversation.getId())
                .data(conversationDetail)
                .timestamp(now)
                .build());
    }

    public QuickChatResponse getQuickChat(QuickChatRequest request) {
        var conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
        var template = chatTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template tidak ditemukan"));

        if (template.getCategory().equals("TEMPLATE_MESSAGE") && conversation.getActiveOrderId() == null) {
            throw new RuntimeException("Pesan template tidak bisa dibuka karena belum ada pesanan aktif");
        }

        QuickChatResponse response = new QuickChatResponse();
        if (template.getCategory().equals("Quick Reply")) {
            response.setMessage(template.getContent());
        } else {
            var templateParam = messageConstructorHelper.buildOrderParams(conversation.getActiveOrderId());
            response.setMessage(messageConstructorHelper.fillTemplate(template.getContent(), templateParam));
        }

        return response;

    }

    public Conversation saveConversation(Conversation conversation) {
        return conversationRepository.save(conversation);
    }

    public Conversation findByIdContact(Long idContact) {
        return conversationRepository.findByIdContact(idContact);
    }

    public Conversation findById(UUID idConversation) {
        return conversationRepository.findById(idConversation)
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
    }

    public Contact findContactByPhoneNumberAndIdWorkspace(String phoneNumber, Long idWorkspace) {
        return contactRepository.findByPhoneNumberAndIdWorkspace(phoneNumber, idWorkspace);
    }

    public Contact saveContact(Contact contact) {
        return contactRepository.save(contact);
    }

    public Conversation getConversationById(UUID idConversation){
        return conversationRepository.findById(idConversation).orElseThrow();
    }

    public Conversation save(Conversation conversation){
        return conversationRepository.save(conversation);
    }

    public Contact getContactByIdAndIdWorkspace(Long idContact, Long idWorkspace){
        var contact = contactRepository.findByIdAndIdWorkspace(idContact, idWorkspace);
        return contact;
    }

    public Conversation getConverSationByContact(Long idContact){
        return conversationRepository.findByIdContact(idContact);

    }

}
