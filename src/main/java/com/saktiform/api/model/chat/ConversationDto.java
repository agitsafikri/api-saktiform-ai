package com.saktiform.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;


public interface ConversationDto {
    UUID getId();
    String getContactName();
    String getLastMessage();
    String getLastMessageType();
    String getLastMessageTime();
    String getStatus();
}
