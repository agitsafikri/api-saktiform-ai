package com.saktiform.api.service.chat;

import com.saktiform.api.model.chat.ConversationUpdatedData;
import com.saktiform.api.model.event.ChatAsyncEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatWebSocketEventListener {

    private final ChatEventPublisher publisher;
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketEventListener.class);

    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @org.springframework.context.event.EventListener
    public void handleChatAsyncEvent(ChatAsyncEvent event) {
        System.out.println("🔥 LISTENER TRIGGERED: " + event.getEventType());
        log.debug("Handling ChatAsyncEvent. Type: {}, Workspace: {}", event.getEventType(), event.getWorkspaceId());

        try {
            switch (event.getEventType()) {
                case CONVERSATION_UPDATED:
                    publisher.publishConversationUpdated(event.getWorkspaceId(),
                            (ConversationUpdatedData) event.getData(), event.getTimestamp());
                    break;
                case CONVERSATION_CREATED:
                    publisher.publishConversationCreated(event.getWorkspaceId(), event.getData(), event.getTimestamp());
                    break;
                case UNASSIGNED_CONVERSATION_UPDATED:
                    publisher.publishUnassignedConversationUpdated(event.getWorkspaceId(),
                            (ConversationUpdatedData) event.getData(), event.getTimestamp());
                    break;
                case UNASSIGNED_CONVERSATION_CREATED:
                    publisher.publishUnassignedConversationCreated(event.getWorkspaceId(), event.getData(),
                            event.getTimestamp());
                    break;
                case UNASSIGNED_CONVERSATION_REMOVED:
                    publisher.publishUnassignedConversationRemoved(event.getWorkspaceId(), event.getData(),
                            event.getTimestamp());
                    break;
                case ASSIGNED_CONVERSATION_UPDATED:
                    publisher.publishAssignedConversationUpdated(event.getWorkspaceId(),
                            (ConversationUpdatedData) event.getData(), event.getTimestamp());
                    break;
                case ASSIGNED_CONVERSATION_CREATED:
                    publisher.publishAssignedConversationCreated(event.getWorkspaceId(), event.getData(),
                            event.getTimestamp());
                    break;
                case ASSIGNED_CONVERSATION_REMOVED:
                    publisher.publishAssignedConversationRemoved(event.getWorkspaceId(), event.getData(),
                            event.getTimestamp());
                    break;
                case NEW_MESSAGE:
                    publisher.publishNewMessage(event.getConversationId(), event.getData(), event.getTimestamp());
                    break;
                case CONVERSATION_DETAIL_UPDATED:
                    publisher.publishConversationDetail(event.getConversationId(), event.getData(),
                            event.getTimestamp());
                    break;
                default:
                    log.warn("Unhandled ChatAsyncEvent type: {}", event.getEventType());
            }
        } catch (ClassCastException e) {
            log.error("Error casting event data for type {}: {}", event.getEventType(), e.getMessage());
        } catch (Exception e) {
            log.error("Error handling ChatAsyncEvent: {}", e.getMessage(), e);
        }
    }
}
