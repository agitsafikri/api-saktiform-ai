package com.saktiform.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationEvent {
    private String eventId; // Unique ID for event deduplication
    private String type; // e.g. CONVERSATION_UPDATED
    private Object data;
    private String timestamp;
}
