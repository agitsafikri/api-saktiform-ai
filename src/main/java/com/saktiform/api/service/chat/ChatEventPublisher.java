package com.saktiform.api.service.chat;

import com.saktiform.api.model.chat.ConversationEvent;
import com.saktiform.api.model.chat.ConversationUpdatedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ChatEventPublisher {
        private static final Logger log = LoggerFactory.getLogger(ChatEventPublisher.class);

        private final SimpMessagingTemplate messagingTemplate;

        public ChatEventPublisher(SimpMessagingTemplate messagingTemplate) {
                this.messagingTemplate = messagingTemplate;
        }

        public void publishConversationUpdated(Long workspaceId, ConversationUpdatedData data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_UPDATED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_UPDATED to workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_UPDATED to workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        public void publishConversationCreated(Long workspaceId, Object data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_CREATED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_CREATED to workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_CREATED to workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        // Unassigned Convo
        public void publishUnassignedConversationUpdated(Long workspaceId, ConversationUpdatedData data,
                        String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_UPDATED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/unassigned/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_UPDATED to unassigned workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_UPDATED to unassigned workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        public void publishUnassignedConversationCreated(Long workspaceId, Object data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_CREATED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/unassigned/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_CREATED to unassigned workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_CREATED to unassigned workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        public void publishUnassignedConversationRemoved(Long workspaceId, Object data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_REMOVED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/unassigned/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_REMOVED to unassigned workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_REMOVED to unassigned workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        // Assigned Convo event
        public void publishAssignedConversationUpdated(Long workspaceId, ConversationUpdatedData data,
                        String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_UPDATED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/assigned/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_UPDATED to assigned workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_UPDATED to assigned workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        public void publishAssignedConversationCreated(Long workspaceId, Object data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_CREATED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/assigned/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_CREATED to assigned workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_CREATED to assigned workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        public void publishAssignedConversationRemoved(Long workspaceId, Object data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_REMOVED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/conversations/assigned/" + workspaceId,
                                        event);

                        log.debug("Published CONVERSATION_REMOVED to assigned workspace {}", workspaceId);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_REMOVED to assigned workspace {}: {}",
                                        workspaceId, e.getMessage(), e);
                }
        }

        // chatroom event
        public void publishNewMessage(UUID idConversation, Object data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "NEW_MESSAGE",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/chatroom/" + idConversation,
                                        event);

                        log.debug("Published NEW_MESSAGE to chatroom {}", idConversation);
                } catch (Exception e) {
                        log.error("Failed to publish NEW_MESSAGE to chatroom {}: {}",
                                        idConversation, e.getMessage(), e);
                }
        }

        public void publishConversationDetail(UUID idConversation, Object data, String timestamp) {
                try {
                        ConversationEvent event = new ConversationEvent(
                                        UUID.randomUUID().toString(),
                                        "CONVERSATION_DETAIL_UPDATED",
                                        data,
                                        timestamp);

                        messagingTemplate.convertAndSend(
                                        "/topic/chatroom/" + idConversation,
                                        event);

                        log.debug("Published CONVERSATION_DETAIL_UPDATED to chatroom {}", idConversation);
                } catch (Exception e) {
                        log.error("Failed to publish CONVERSATION_DETAIL_UPDATED to chatroom {}: {}",
                                        idConversation, e.getMessage(), e);
                }
        }
}
