package com.saktiform.api.service.order;

import com.saktiform.api.entity.Conversation;
import com.saktiform.api.entity.Order;
import com.saktiform.api.entity.Produk;
import com.saktiform.api.entity.WhatsappBusinessApi;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.Order.CreateOrderDto;
import com.saktiform.api.model.Order.OrderCreatedEvent;
import com.saktiform.api.model.Order.OrderCreatedNotification;
import com.saktiform.api.model.Order.OrderPlacedEvent;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.chat.ChatAddOrderRequest;
import com.saktiform.api.model.chat.ConversationEvent;
import com.saktiform.api.repository.ContactRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.ProdukService;
import com.saktiform.api.service.chat.ChatEventPublisher;
import com.saktiform.api.service.chat.ConversationService;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import com.saktiform.api.service.formconfig.CustomFieldValueValidator;
import com.saktiform.api.service.formconfig.OrderCustomFieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Notifikasi "order baru" per workspace: hanya order checkout yang dihitung,
 * dikirim ke {@code /topic/orders/{workspaceId}} dengan payload minimal.
 */
class OrderPlacedNotificationTest {

    private static final Long WORKSPACE_ID = 7L;
    private static final Instant CREATED_AT = Instant.parse("2026-11-23T03:15:00Z");

    private OrderService orderService;
    private ConversationService conversationService;
    private ApplicationEventPublisher eventPublisher;
    private OrderOrchestrationService orchestration;

    private final UUID orderId = UUID.randomUUID();
    private final UUID produkId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        conversationService = mock(ConversationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        ProdukService produkService = mock(ProdukService.class);

        orchestration = new OrderOrchestrationService(orderService, conversationService, workspaceRepository,
                mock(MessageConstructorHelper.class), produkService, eventPublisher,
                mock(ContactRepository.class), mock(CustomFieldValueValidator.class),
                mock(OrderCustomFieldService.class));

        Produk produk = new Produk();
        produk.setId(produkId);
        produk.setIdWorkspace(WORKSPACE_ID);
        when(produkService.findProdukById(produkId)).thenReturn(produk);

        Workspace workspace = new Workspace();
        workspace.setWaba(new WhatsappBusinessApi());
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
    }

    private Order order(String source) {
        Order order = new Order();
        order.setId(orderId);
        order.setIdProduk(produkId);
        order.setSource(source);
        order.setPembayaran("COD");
        order.setStatus("UNPAID");
        order.setCreatedAt(CREATED_AT);
        return order;
    }

    private CreateOrderDto dto(String source) {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setIdProduk(produkId);
        dto.setSource(source);
        return dto;
    }

    @Test
    @DisplayName("Order checkout memicu OrderPlacedEvent dengan workspace produk")
    void checkoutOrderPublishesEvent() {
        when(orderService.createOrderInternal(any(), any(), any())).thenReturn(order("FORM"));

        orchestration.createOrder(dto("FORM"), "pelanggan", "127.0.0.1");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<OrderPlacedEvent> placed = captor.getAllValues().stream()
                .filter(OrderPlacedEvent.class::isInstance).map(OrderPlacedEvent.class::cast).toList();
        assertEquals(List.of(new OrderPlacedEvent(orderId, WORKSPACE_ID, CREATED_AT)), placed);
    }

    @Test
    @DisplayName("Order abandoned tidak memicu notifikasi")
    void abandonedOrderDoesNotPublish() {
        orchestration.createOrder(dto("abandoned"), "pelanggan", "127.0.0.1");

        verify(eventPublisher, never()).publishEvent(isA(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("Order agen dari Chat (CST_CHAT) tidak memicu notifikasi")
    void agentChatOrderDoesNotPublish() {
        Order order = order("CST_CHAT");
        when(orderService.createOrderInternal(any(), any(), any())).thenReturn(order);
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        when(conversationService.getOrCreateByContact(any())).thenReturn(conversation);

        ChatAddOrderRequest request = new ChatAddOrderRequest();
        request.setIdProduk(produkId);
        request.setMetodePembayaran("COD");
        request.setStatus(OrderStatus.UNPAID);

        orchestration.createOrderOnChat(request, "agen");

        verify(eventPublisher, never()).publishEvent(isA(OrderPlacedEvent.class));
        // Follow-up WhatsApp yang lama tetap jalan.
        verify(eventPublisher).publishEvent(isA(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("Listener mengirim ORDER_CREATED ke topic workspace dengan payload minimal")
    void listenerPublishesToWorkspaceTopic() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        OrderNotificationListener listener = new OrderNotificationListener(new ChatEventPublisher(template));

        listener.onOrderPlaced(new OrderPlacedEvent(orderId, WORKSPACE_ID, CREATED_AT));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/orders/" + WORKSPACE_ID), payload.capture());
        ConversationEvent event = (ConversationEvent) payload.getValue();
        assertEquals("ORDER_CREATED", event.getType());
        assertNotNull(event.getEventId());
        assertEquals("2026-11-23T03:15:00Z", event.getTimestamp());
        assertEquals(new OrderCreatedNotification(orderId, "2026-11-23T03:15:00Z"), event.getData());
    }
}
