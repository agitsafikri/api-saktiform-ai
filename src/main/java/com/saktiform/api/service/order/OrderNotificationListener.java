package com.saktiform.api.service.order;

import com.saktiform.api.model.Order.OrderCreatedNotification;
import com.saktiform.api.model.Order.OrderPlacedEvent;
import com.saktiform.api.service.chat.ChatEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mengabarkan order baru ke dashboard workspace terkait lewat WebSocket.
 * Berjalan setelah commit (order yang di-rollback tidak memicu notifikasi) dan
 * asinkron (tidak memperlambat checkout).
 */
@Component
public class OrderNotificationListener {

    private final ChatEventPublisher publisher;

    public OrderNotificationListener(ChatEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        String createdAt = event.createdAt().toString();
        publisher.publishOrderCreated(event.workspaceId(),
                new OrderCreatedNotification(event.orderId(), createdAt), createdAt);
    }
}
