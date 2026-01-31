package com.saktiform.api.service;

import com.saktiform.api.entity.Conversation;
import com.saktiform.api.entity.Order;
import com.saktiform.api.model.Order.CreateOrderDto;
import com.saktiform.api.model.Order.OrderCreatedEvent;
import com.saktiform.api.model.Order.OrderCreatedResponse;
import com.saktiform.api.model.chat.ChatAddOrderRequest;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.chat.ConversationService;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderOrchestrationService {

    private final OrderService orderService;
    private final ConversationService conversationService;
    private final WorkspaceRepository workspaceRepository;
    private final MessageConstructorHelper messageConstructorHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final ProdukService produkService;

    public OrderOrchestrationService(
            OrderService orderService,
            ConversationService conversationService,
            WorkspaceRepository workspaceRepository,
            MessageConstructorHelper messageConstructorHelper,
            ProdukService produkService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderService = orderService;
        this.conversationService = conversationService;
        this.workspaceRepository = workspaceRepository;
        this.messageConstructorHelper = messageConstructorHelper;
        this.eventPublisher = eventPublisher;
        this.produkService = produkService;
    }

    @Transactional
    public OrderCreatedResponse createOrder(CreateOrderDto data) {

        // 1. Create order (PURE)
        Order order = orderService.createOrderInternal(data);

        // 2. Get or create conversation
        Conversation conversation =
                conversationService.getOrCreateByContact(order.getIdContact());

        // 3. Attach conversation to order
        orderService.attachConversation(order.getId(), conversation.getId());

        // 4. Publish event
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));

        // 5. Response WA
        var produk = produkService.findProdukById(order.getIdProduk());
        var workspace = workspaceRepository
                .findById(produk.getIdWorkspace()).get();

        OrderCreatedResponse response = new OrderCreatedResponse();
        response.setPhoneNumber(workspace.getWaba().getNomorWhatsapp());
        response.setMessage(
                messageConstructorHelper.confirmationMessage(
                        order.getNamaPenerima(),
                        order.getOrderCode()
                )
        );

        return response;
    }

    @Transactional
    public OrderCreatedResponse createOrderOnChat(ChatAddOrderRequest data) {
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
                "CST_CHAT"
        );


        // 1. Create order (PURE)
        Order order = orderService.createOrderInternal(newOrder);

        order.setNotes(data.getNotes() == null ? "" : data.getNotes());
        order.setDiskon(data.getDiskon() == null ? 0 : data.getDiskon());
        order.setStatus(data.getStatus().name());

        // 2. Get or create conversation
        Conversation conversation =
                conversationService.getOrCreateByContact(order.getIdContact());

        // 3. Attach conversation to order
        orderService.attachConversation(order.getId(), conversation.getId());

        // 4. Publish event
        if(!order.getSource().equals("CST_CHAT")){
            eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));
        }


        // 5. Response WA
        var produk = produkService.findProdukById(order.getIdProduk());
        var workspace = workspaceRepository
                .findById(produk.getIdWorkspace()).get();

        OrderCreatedResponse response = new OrderCreatedResponse();
        response.setPhoneNumber(workspace.getWaba().getNomorWhatsapp());
        response.setMessage(
                messageConstructorHelper.confirmationMessage(
                        order.getNamaPenerima(),
                        order.getOrderCode()
                )
        );

        return response;
    }
}
