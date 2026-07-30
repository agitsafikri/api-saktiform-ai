package com.saktiform.api.service.order;

import com.saktiform.api.entity.Contact;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.entity.Order;
import com.saktiform.api.model.Order.CreateOrderDto;
import com.saktiform.api.model.Order.OrderCreatedEvent;
import com.saktiform.api.model.Order.OrderCreatedResponse;
import com.saktiform.api.model.Order.UpdateOrderDto;
import com.saktiform.api.model.chat.ChatAddOrderRequest;
import com.saktiform.api.model.chat.bot.OrderChatInfo;
import com.saktiform.api.repository.ContactRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.ProdukService;
import com.saktiform.api.service.chat.ConversationService;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import com.saktiform.api.service.formconfig.CustomFieldValueValidator;
import com.saktiform.api.service.formconfig.OrderCustomFieldService;
import com.saktiform.api.service.formconfig.ValidatedFieldValue;
import com.saktiform.api.util.PhoneNumberUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderOrchestrationService {

    private final OrderService orderService;
    private final ConversationService conversationService;
    private final WorkspaceRepository workspaceRepository;
    private final MessageConstructorHelper messageConstructorHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final ProdukService produkService;
    private final ContactRepository contactRepository;
    private final CustomFieldValueValidator customFieldValueValidator;
    private final OrderCustomFieldService orderCustomFieldService;

    public OrderOrchestrationService(
            OrderService orderService,
            ConversationService conversationService,
            WorkspaceRepository workspaceRepository,
            MessageConstructorHelper messageConstructorHelper,
            ProdukService produkService,
            ApplicationEventPublisher eventPublisher,
            ContactRepository contactRepository,
            CustomFieldValueValidator customFieldValueValidator,
            OrderCustomFieldService orderCustomFieldService) {
        this.orderService = orderService;
        this.conversationService = conversationService;
        this.workspaceRepository = workspaceRepository;
        this.messageConstructorHelper = messageConstructorHelper;
        this.eventPublisher = eventPublisher;
        this.produkService = produkService;
        this.contactRepository = contactRepository;
        this.customFieldValueValidator = customFieldValueValidator;
        this.orderCustomFieldService = orderCustomFieldService;
    }

    @Transactional
    public OrderCreatedResponse createOrder(CreateOrderDto data, String actor, String ip) {

        if(data.getSource().equalsIgnoreCase("abandoned") || data.getSource().equalsIgnoreCase("abandon") || data.getSource().equalsIgnoreCase("abandoned_order")){
            orderService.saveAbandonedOrder(data);
            return null;
        }

        // 0. Validasi Custom Field DIDAHULUKAN — sebelum penulisan apa pun.
        //    Bila dijalankan setelah createOrderInternal, kegagalan validasi memicu
        //    rollback namun nomor urut order dan orderCount produk sudah terlanjur
        //    bertambah (efek samping yang tidak ikut ter-rollback).
        List<ValidatedFieldValue> validatedCustomFields = customFieldValueValidator.validate(
                data.getIdProduk(), data.getCustomFields(), data.getSource());

        // 1. Create order (PURE)
        Order order = orderService.createOrderInternal(data, actor, ip);

        // 1b. Snapshot Custom Field — satu batch insert
        orderCustomFieldService.saveSnapshot(order.getId(), order.getIdProduk(), validatedCustomFields);

        // 2. Get or create conversation
        //Conversation conversation = conversationService.getOrCreateByContact(order.getIdContact());

        // 3. Attach conversation to order
        //orderService.attachConversation(order.getId(), conversation.getId());

        // 4. Publish event
        if(!order.getSource().equals("CST_CHAT")){
            if (order.getPembayaran().equals("COD")){
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "FOLLOWUP-COD"));
            }else {
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "FOLLOWUP-TRANSFER"));
            }

        }

        // 5. Response WA
        var produk = produkService.findProdukById(order.getIdProduk());
        var workspace = workspaceRepository
                .findById(produk.getIdWorkspace()).get();

        OrderCreatedResponse response = new OrderCreatedResponse();
        response.setPhoneNumber(workspace.getWaba().getNomorWhatsapp());
        response.setMessage(
                messageConstructorHelper.confirmationMessage(
                        produk.getNamaProduk(),
                        order.getNamaPenerima()
                )
        );

        return response;
    }

    @Transactional
    public void createOrderOnChat(ChatAddOrderRequest data, String actor) {
        CreateOrderDto newOrder = new CreateOrderDto(
                data.getIdProduk(),
                data.getIdAtributProduk(),
                data.getNamaLengkap(),
                data.getNomorWhatsapp(),
                data.getAlamat(),
                data.getIdProvinsi(),
                data.getIdKota(),
                data.getIdKecamatan(),
                data.getMetodePembayaran(),
                "CST_CHAT",
                // Jalur agen belum mengumpulkan Custom Field; validasi wajib-isi juga
                // dilewati untuk source CST_CHAT agar operasional agen tidak terblokir.
                null
        );


        // 1. Create order (PURE)
        Order order = orderService.createOrderInternal(newOrder, actor, null);

        order.setNotes(data.getNotes() == null ? "" : data.getNotes());
        order.setDiskon(data.getDiskon() == null ? 0 : data.getDiskon());
        order.setStatus(data.getStatus().name());

        if(data.getStatus().name().equals("PAID")){
            order.setPaidAt(Instant.now());
            orderService.increaseProdukSoldCount(order.getIdProduk());
            orderService.createLogs(order, "Pesanan ditandai sebagai PAID oleh "+actor);
        }

        // 2. Get or create conversation
        Conversation conversation =
                conversationService.getOrCreateByContact(order.getIdContact());

        // 3. Attach conversation to order
        orderService.attachConversation(order.getId(), conversation.getId());

        // 4. Publish event
        if(order.getSource().equals("CST_CHAT")){
            if (order.getPembayaran().equals("COD")){
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "CONFIRMATION-COD"));
            }else {
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "CONFIRMATION-TRANSFER"));
            }

        }



    }

    @Transactional
    public void updateOrder(UpdateOrderDto data, String actor){
        //var contact = orderService.getOrderById(data.getId()).getIdContact();
        var order = orderService.updateOrder(data, actor);
        //var prevConversation = order.getConversation().getId();
        // 2. Get or create conversation
        //Conversation conversation = conversationService.getOrCreateByContact(order.getIdContact());

        // 3. Attach conversation to order
        //orderService.attachConversation(order.getId(), conversation.getId());
//        if(prevConversation == null || !prevConversation.equals(conversation.getId())){
//            if (conversation.getStatus().equals("UNASSIGNED") && conversation.getHandleByBot() == true){
//                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));
//            }
//        }
        if (!order.getNomorWhatsapp().equals(PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp()))){
            if (order.getPembayaran().equals("COD")){
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "FOLLOWUP-COD"));
            }else {
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "FOLLOWUP-TRANSFER"));
            }
        }


    }
    @Transactional
    public Order orderConfirmation(OrderChatInfo orderChatInfo, UUID idConversation){
        var order = orderService.getConfirmOrder(orderChatInfo, idConversation);

        if(order != null){
            //orderService.attachConversation(order.getId(), idConversation);
            var conversation = conversationService.getConversationById(idConversation);
            Contact contact = contactRepository.findById(conversation.getIdContact()).get();
            contact.setNamaKontak(order.getNamaPenerima());
            order.setIdConversation(conversation.getId());
            order.setIdContact(conversation.getIdContact());
            order.setNomorWhatsapp(conversation.getContact().getPhoneNumber());


            // 4. Publish event
            if (order.getPembayaran().equals("COD")){
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "CONFIRMATION-COD"));
            }else {
                eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), "CONFIRMATION-TRANSFER"));
            }
        }
        return order;
    }
}
