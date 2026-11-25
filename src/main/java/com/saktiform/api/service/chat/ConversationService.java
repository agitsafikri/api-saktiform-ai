package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Contact;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.Order.ConversationOrderList;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.chat.ConversationStatus;
import com.saktiform.api.model.account.ConversationDetail;
import com.saktiform.api.model.chat.*;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.model.label.response.LabelDto;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.ChatTemplateMediaMapper;
import com.saktiform.api.service.StorageService;
import com.saktiform.api.service.label.ConversationLabelService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    private final OrderRepository orderRepository;
    private final ConversationLabelService labelService;
    private final ChatTemplateMediaMapper chatTemplateMediaMapper;
    private final AccountRepository accountRepository;


//    @Value("${saktiform.api.url}")
//    private String urlApi;

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ConversationService(ConversationRepository conversationRepository,
            ChatRepository chatRepository,
            ChatTemplateRepository chatTemplateRepository,
            MessageConstructorHelper messageConstructorHelper,
            ContactRepository contactRepository,
            StorageService storageService,
            OrderRepository orderRepository,
            ConversationLabelService labelService,
            ChatTemplateMediaMapper chatTemplateMediaMapper,
            AccountRepository accountRepository,
            ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.chatRepository = chatRepository;
        this.orderRepository = orderRepository;
        this.conversationRepository = conversationRepository;
        this.chatTemplateRepository = chatTemplateRepository;
        this.messageConstructorHelper = messageConstructorHelper;
        this.eventPublisher = eventPublisher;
        this.contactRepository = contactRepository;
        this.storageService = storageService;
        this.labelService = labelService;
        this.chatTemplateMediaMapper = chatTemplateMediaMapper;
    }

    public Page<ConversationListItemDto> getUnassignedChat(Long idWorkspace, Integer page, Integer limit, Boolean isUnread, String agent, LocalDateTime dateStart, LocalDateTime dateEnd, String statusOrder, String keyword, String chatStatus, List<Long> labelIds, String jenisPembayaran) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "last_message_at"));
        if (dateEnd != null && dateEnd.isAfter(LocalDateTime.now())){
            dateEnd = LocalDateTime.now();
        }
        var agentId = conversationRepository.getAgentId(agent);

        boolean labelFilter = labelIds != null && !labelIds.isEmpty();
        Collection<Long> ids = labelFilter ? labelIds : List.of(-1L);

        var pageResult = conversationRepository.getConversation(idWorkspace, "UNASSIGNED", dateStart, dateEnd, isUnread, agentId, statusOrder, keyword, chatStatus, labelFilter, ids, jenisPembayaran ,pageable);
        return withLabels(pageResult);
    }

    public Page<ConversationListItemDto> getAssignedChat(Long idWorkspace, Integer page, Integer limit, Boolean isUnread, String agentName, LocalDateTime dateStart, LocalDateTime dateEnd, String statusOrder, String keyword, List<Long> labelIds, String jenisPembayaran) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "last_message_at"));
        if (dateEnd != null && dateEnd.isAfter(LocalDateTime.now())){
            dateEnd = LocalDateTime.now();
        }
        var agentId = conversationRepository.getAgentId(agentName);

        boolean labelFilter = labelIds != null && !labelIds.isEmpty();
        Collection<Long> ids = labelFilter ? labelIds : List.of(-1L);

        var pageResult = conversationRepository.getConversation(idWorkspace, "ASSIGNED", dateStart, dateEnd, isUnread, agentId, statusOrder, keyword, null, labelFilter, ids, jenisPembayaran, pageable);
        return withLabels(pageResult);
    }

    /** Batch-hydrate label untuk seluruh conversation dalam satu halaman (anti N+1) → DTO list. */
    private Page<ConversationListItemDto> withLabels(Page<ConversationDto> page) {
        List<UUID> convIds = page.getContent().stream().map(ConversationDto::getId).toList();
        Map<UUID, List<LabelDto>> byConv = labelService.labelsByConversationIds(convIds);
        return page.map(p -> new ConversationListItemDto(p, byConv.get(p.getId())));
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
        conversationDetail.setStatus(conversation.getChatStatus());

        conversationDetail.setSelectedOrder(resolveActiveOrderCode(conversation));

        // Label terpasang — workspace diturunkan dari conversation itu sendiri (guard konsisten).
        var idWorkspace = conversation.getContact() != null ? conversation.getContact().getIdWorkspace() : null;
        if (idWorkspace != null) {
            conversationDetail.setLabels(labelService.listForConversation(idConversation, idWorkspace));
        } else {
            conversationDetail.setLabels(List.of());
        }

        return conversationDetail;
    }

    @Transactional
    public void selectConversationOrder(ConversationSelectOrder order) {
        var conversation = conversationRepository.findById(order.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
        if (order.getOrderId() != null
                && orderRepository.existsByIdAndStatus(order.getOrderId(), OrderStatus.DELETE.name())) {
            throw new RuntimeException("Pesanan yang sudah dihapus tidak bisa dijadikan pesanan aktif");
        }
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

        // Order aktif yang sudah DELETE diperlakukan sama dengan tidak ada pesanan aktif.
        boolean hasActiveOrder = !resolveActiveOrderCode(conversation).isEmpty();
        if (template.getCategory().equals("TEMPLATE_MESSAGE") && !hasActiveOrder) {
            throw new RuntimeException("Pesan template tidak bisa dibuka karena belum ada pesanan aktif");
        }

        QuickChatResponse response = new QuickChatResponse();
        if (template.getCategory().equals("Quick Reply")) {
            response.setMessage(template.getContent());
        } else {
            var templateParam = messageConstructorHelper.buildOrderParams(conversation.getActiveOrderId());
            response.setMessage(messageConstructorHelper.fillTemplate(template.getContent(), templateParam));
        }
        response.setMediaLinks(chatTemplateMediaMapper.toPublicUrls(template));

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

    /**
     * Daftar order sebuah conversation. Mekanisme visibilitas sama dengan list order di modul
     * order: hanya OWNER yang melihat order berstatus DELETE.
     */
    public List<ConversationOrderList> getConversationOrder(UUID idConversation, String username) {
        var conversation = conversationRepository.findById(idConversation)
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
        boolean isOwner = accountRepository.isOwner(username);
        var listOrder = orderRepository.getConversationOrderList(idConversation, isOwner);
        var activeOrderId = conversation.getActiveOrderId();
        if (activeOrderId != null) {
            // Order DELETE (hanya terlihat oleh OWNER) tidak pernah ditandai sebagai terpilih.
            listOrder.forEach(order ->
                    order.setIsSelected(activeOrderId.equals(order.getId())
                            && !OrderStatus.DELETE.name().equals(order.getStatus()))
            );
        }
        return listOrder;
    }

    /** Kode order aktif, atau "" bila tidak ada / sudah berstatus DELETE. */
    private String resolveActiveOrderCode(Conversation conversation) {
        if (conversation.getActiveOrderId() == null) {
            return "";
        }
        return orderRepository.findActiveOrderCode(conversation.getActiveOrderId()).orElse("");
    }



}
