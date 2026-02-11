package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Contact;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.ConversationStatus;
import com.saktiform.api.model.account.ConversationDetail;
import com.saktiform.api.model.chat.*;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.OrderOrchestrationService;
import com.saktiform.api.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final ChatRepository chatRepository;
    private final ChatTemplateRepository chatTemplateRepository;
    private final MessageConstructorHelper messageConstructorHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final StorageService storageService;


//    @Value("${saktiform.api.url}")
//    private String urlApi;

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ConversationService(ConversationRepository conversationRepository,
            ChatRepository chatRepository,
            ChatTemplateRepository chatTemplateRepository,
            MessageConstructorHelper messageConstructorHelper,
            ContactRepository contactRepository,
            StorageService storageService,
            ApplicationEventPublisher eventPublisher) {
        this.chatRepository = chatRepository;
        this.conversationRepository = conversationRepository;
        this.chatTemplateRepository = chatTemplateRepository;
        this.messageConstructorHelper = messageConstructorHelper;
        this.eventPublisher = eventPublisher;
        this.contactRepository = contactRepository;
        this.storageService = storageService;
    }

    public Page<ConversationDto> getUnassignedChat(Long idWorkspace, Integer page, Integer limit, Boolean isUnread, String agent, LocalDateTime dateStart, LocalDateTime dateEnd, String statusOrder, String keyword) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "ch_last.sent_at"));
        var tomorow = LocalDateTime.now().plusDays(1L);
        var sentinel = LocalDateTime.of(1970, 1,1,0,0,0);
        if (dateEnd != null && dateEnd.isAfter(LocalDateTime.now())){
            dateEnd = LocalDateTime.now();
        }
        var agentId = conversationRepository.getAgentId(agent);

        return conversationRepository.getConversation(idWorkspace, "UNASSIGNED", dateStart, dateEnd, sentinel, tomorow, isUnread, agentId, statusOrder, keyword == null?null:keyword.toLowerCase(), pageable);
    }

    public Page<ConversationDto> getAssignedChat(Long idWorkspace, Integer page, Integer limit, Boolean isUnread, String agentName, LocalDateTime dateStart, LocalDateTime dateEnd, String statusOrder, String keyword) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "ch_last.sent_at"));
        var tomorow = LocalDateTime.now().plusDays(1L);
        var sentinel = LocalDateTime.of(1970, 1,1,0,0,0);
        if (dateEnd != null && dateEnd.isAfter(LocalDateTime.now())){
            dateEnd = LocalDateTime.now();
        }
        var agentId = conversationRepository.getAgentId(agentName);
        return conversationRepository.getConversation(idWorkspace, "ASSIGNED", dateStart, dateEnd, sentinel, tomorow, isUnread, agentId, statusOrder, keyword == null ?null:keyword.toLowerCase(), pageable);
    }

    public Page<ChatListDto> getListMessage(UUID idConversation, Integer page, Integer limit, String keyword) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "sentAt"));
        var listChat =  chatRepository.getMessageList(idConversation, keyword, pageable);
        listChat.forEach(data ->{
            if(data.getMediaLink() != null && !data.getMediaLink().isEmpty()){
                //data.setMediaLink(urlApi + data.getMediaLink());
                data.setMediaLink(storageService.getPublicUrl(data.getMediaLink()));
            }

            if(data.getRepliedMessage() != null){
                if(data.getRepliedMessage().getMediaLink() != null && !data.getRepliedMessage().getMediaLink().isEmpty()){
                    data.getRepliedMessage().setMediaLink(storageService.getPublicUrl(data.getRepliedMessage().getMediaLink()));
                }
            }
        });

        var conversation = conversationRepository.findById(idConversation).get();
        conversation.setUnreadMessageCount(0);
        conversation.setIsUnread(false);
        conversationRepository.save(conversation);



        return listChat;
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
        conversationDetail.setPhoneNumber(conversation.getContact().getPhoneNumber());
        conversationDetail.setNamaKontak(conversation.getContact().getNamaKontak());

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

    public Conversation getOrCreateByContact(Long idContact) {


        Conversation conversation =
                conversationRepository.findByIdContact(idContact);


        if (conversation != null) {
            return conversation;
        }


        Conversation newConversation = new Conversation();
        newConversation.setIdContact(idContact);
        newConversation.setStatus(ConversationStatus.UNASSIGNED.name());
        newConversation.setCreatedAt(Instant.now());
        newConversation.setHandleByBot(true);


        return conversationRepository.save(newConversation);
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

    public List<String> getChatAgent (Long idWorkspace){
        return conversationRepository.getAgentByWorkspace(idWorkspace);
    }



}
