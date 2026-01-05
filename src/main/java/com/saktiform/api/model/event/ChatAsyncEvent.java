package com.saktiform.api.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatAsyncEvent {
    private EventType eventType;
    private Long workspaceId;
    private UUID conversationId;
    private Object data;
    private String timestamp;

    public enum EventType {
        CONVERSATION_UPDATED,
        CONVERSATION_CREATED,
        UNASSIGNED_CONVERSATION_UPDATED,
        UNASSIGNED_CONVERSATION_CREATED,
        UNASSIGNED_CONVERSATION_REMOVED,
        ASSIGNED_CONVERSATION_UPDATED,
        ASSIGNED_CONVERSATION_CREATED,
        ASSIGNED_CONVERSATION_REMOVED,
        NEW_MESSAGE,
        CONVERSATION_DETAIL_UPDATED
    }
}
